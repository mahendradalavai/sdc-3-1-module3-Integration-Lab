"""
data_processing.py
Server-side document ingestion, text normalization, and recursive chunking pipeline.
Integration Lab - Mobile Document Assistant
"""

import re
import uuid
import time
from typing import List, Dict, Any, Optional


def clean_text(text: str) -> str:
    """Normalize whitespace and strip non-printable characters."""
    if not text:
        return ""
    # Normalize unicode spaces and newlines
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    # Collapse 3+ newlines into double newlines
    text = re.sub(r'\n{3,}', '\n\n', text)
    # Strip trailing/leading whitespace per line
    lines = [line.strip() for line in text.split("\n")]
    return "\n".join(lines).strip()


def estimate_tokens(text: str) -> int:
    """Rough estimation of token count (~4 characters per token)."""
    return max(1, len(text) // 4)


def recursive_chunk_text(
    text: str,
    chunk_size: int = 400,
    chunk_overlap: int = 80
) -> List[Dict[str, Any]]:
    """
    Recursively splits text into overlapping semantic chunks prioritizing:
    1. Double line breaks (paragraphs)
    2. Single line breaks (lines)
    3. Sentence terminators (. ! ?)
    4. Words / character boundaries
    """
    cleaned = clean_text(text)
    if not cleaned:
        return []

    if len(cleaned) <= chunk_size:
        return [{
            "chunk_index": 0,
            "text": cleaned,
            "char_count": len(cleaned),
            "estimated_tokens": estimate_tokens(cleaned),
            "start_char": 0,
            "end_char": len(cleaned)
        }]

    # Step 1: Split into candidate paragraphs
    paragraphs = cleaned.split("\n\n")
    chunks: List[Dict[str, Any]] = []
    current_chunk = ""
    current_start = 0

    for para in paragraphs:
        para = para.strip()
        if not para:
            continue

        # If adding this paragraph exceeds chunk_size, flush or split
        if len(current_chunk) + len(para) + 2 > chunk_size and len(current_chunk) > 0:
            chunks.append({
                "chunk_index": len(chunks),
                "text": current_chunk.strip(),
                "char_count": len(current_chunk.strip()),
                "estimated_tokens": estimate_tokens(current_chunk.strip()),
                "start_char": current_start,
                "end_char": current_start + len(current_chunk.strip())
            })
            # Overlap handling: retain trailing characters
            overlap_prefix = current_chunk[-chunk_overlap:] if len(current_chunk) >= chunk_overlap else current_chunk
            current_start += len(current_chunk) - len(overlap_prefix)
            current_chunk = overlap_prefix + "\n\n" + para
        else:
            if current_chunk:
                current_chunk += "\n\n" + para
            else:
                current_chunk = para

    # Flush remainder
    if current_chunk.strip():
        chunks.append({
            "chunk_index": len(chunks),
            "text": current_chunk.strip(),
            "char_count": len(current_chunk.strip()),
            "estimated_tokens": estimate_tokens(current_chunk.strip()),
            "start_char": current_start,
            "end_char": current_start + len(current_chunk.strip())
        })

    # Sub-divide any unusually large chunks by sentence
    refined_chunks: List[Dict[str, Any]] = []
    for c in chunks:
        if c["char_count"] > chunk_size * 1.5:
            sentences = re.split(r'(?<=[.!?])\s+', c["text"])
            sub_buf = ""
            sub_idx = 0
            for sent in sentences:
                if len(sub_buf) + len(sent) > chunk_size and sub_buf:
                    refined_chunks.append({
                        "chunk_index": len(refined_chunks),
                        "text": sub_buf.strip(),
                        "char_count": len(sub_buf.strip()),
                        "estimated_tokens": estimate_tokens(sub_buf.strip()),
                        "start_char": c["start_char"] + sub_idx,
                        "end_char": c["start_char"] + sub_idx + len(sub_buf.strip())
                    })
                    sub_idx += len(sub_buf)
                    sub_buf = sent
                else:
                    sub_buf += (" " if sub_buf else "") + sent
            if sub_buf.strip():
                refined_chunks.append({
                    "chunk_index": len(refined_chunks),
                    "text": sub_buf.strip(),
                    "char_count": len(sub_buf.strip()),
                    "estimated_tokens": estimate_tokens(sub_buf.strip()),
                    "start_char": c["start_char"] + sub_idx,
                    "end_char": c["start_char"] + sub_idx + len(sub_buf.strip())
                })
        else:
            c["chunk_index"] = len(refined_chunks)
            refined_chunks.append(c)

    return refined_chunks


def process_document(
    title: str,
    content: str,
    category: str = "General",
    author: str = "Integration Lab",
    chunk_size: int = 400,
    chunk_overlap: int = 80
) -> Dict[str, Any]:
    """
    Takes a raw document and produces indexed document structure with chunks ready for embedding.
    """
    doc_id = str(uuid.uuid4())[:8]
    chunks = recursive_chunk_text(content, chunk_size=chunk_size, chunk_overlap=chunk_overlap)
    
    total_tokens = sum(c["estimated_tokens"] for c in chunks)
    
    # Attach doc_id to each chunk
    for c in chunks:
        c["doc_id"] = doc_id
        c["doc_title"] = title
        c["category"] = category

    return {
        "id": doc_id,
        "title": title,
        "category": category,
        "author": author,
        "created_at": int(time.time() * 1000),
        "total_chars": len(content),
        "total_tokens": total_tokens,
        "chunk_count": len(chunks),
        "chunks": chunks
    }


def get_sample_lab_documents() -> List[Dict[str, Any]]:
    """Generates a rich suite of realistic integration lab documents for vector search."""
    return [
        {
            "title": "Vector Databases and Approximate Nearest Neighbor (ANN)",
            "category": "Vector Search",
            "author": "AI Research Lab",
            "content": """Vector databases index high-dimensional embeddings using algorithms like HNSW (Hierarchical Navigable Small World) and IVF-PQ (Inverted File with Product Quantization). 
HNSW constructs a multi-layer graph where upper layers feature long-range connections for fast greedy routing, and lower layers offer granular local connectivity. This enables logarithmic search complexity O(log N).

Product Quantization compresses continuous vectors into compact byte codes, enabling billions of vectors to reside in system memory. 
Cosine similarity measures the angle between two unit-normalized vectors:
cos(u, v) = (u . v) / (||u|| * ||v||).
When vectors are L2-normalized, cosine similarity equals the dot product, allowing SIMD hardware acceleration.

In contrast, Euclidean distance (L2 distance) measures geometric straight-line separation. Cosine similarity is preferred in Natural Language Processing because it assesses semantic orientation irrespective of document token length."""
        },
        {
            "title": "Retrieval-Augmented Generation (RAG) Architecture",
            "category": "System Architecture",
            "author": "Systems Engineering",
            "content": """Retrieval-Augmented Generation mitigates hallucination in Large Language Models by grounding outputs in retrieved domain knowledge.
The RAG pipeline operates in three discrete stages:
1. Ingestion: Documents are extracted, cleaned, chunked into optimal semantic windows (256 to 512 tokens), and embedded into a vector space.
2. Retrieval: When a query arrives, it is embedded using the same vectorizer. Top-K nearest neighbors are retrieved based on cosine similarity thresholds.
3. Generation: A grounded system prompt combines the user question with the retrieved chunks as authoritative context.

Advanced RAG techniques incorporate re-ranking using Cross-Encoders, hybrid BM25 + dense vector search, and recursive hierarchical chunking. Citation attribution ensures every factual claim maps back to verifiable source documents."""
        },
        {
            "title": "Edge AI and On-Device Embedding Inference",
            "category": "Mobile Systems",
            "author": "Mobile Core Team",
            "content": """Deploying vector search on mobile clients requires careful memory budget and battery optimization.
Mobile SQLite with Room database can store chunk representations alongside quantized embedding vectors. 
By storing normalized float arrays or int8 quantized embeddings, mobile devices can execute cosine similarity scans across thousands of document chunks in sub-10ms latency using Kotlin coroutines and SIMD acceleration.

Hybrid mobile architectures utilize local on-device caches for instant offline retrieval while synchronizing with Python backend inference servers for computationally intensive re-ranking or global multi-tenant vector searches.
Network failover strategies ensure the mobile document assistant seamlessly falls back to on-device cosine matching when offline."""
        },
        {
            "title": "Zero-Trust API Security in Integration Labs",
            "category": "Security & Infra",
            "author": "Security Ops",
            "content": """Microservice architectures connecting mobile frontends to AI model backends must enforce strict zero-trust standards.
Every endpoint requires mTLS or signed JWT authentication. Rate limiting via token-bucket algorithms prevents denial-of-service vector search spikes.
Embedding generation endpoints should implement request batching to saturate GPU vector engines efficiently without causing thread starvation.

Sanitization filters must check ingested documents for prompt injection attacks and malicious markdown payloads before chunks enter the vector indexing pipeline."""
        }
    ]


if __name__ == "__main__":
    docs = get_sample_lab_documents()
    print(f"Loaded {len(docs)} sample lab documents.")
    for d in docs:
        processed = process_document(d["title"], d["content"], d["category"], d["author"])
        print(f"-> '{processed['title']}': {processed['chunk_count']} chunks, ~{processed['total_tokens']} tokens.")
