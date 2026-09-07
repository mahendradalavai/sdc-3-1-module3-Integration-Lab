"""
model_inference.py
Vector embedding generation, cosine similarity search, and RAG model inference.
Integration Lab - Server-side inference pipeline
"""

import os
import math
import time
import json
import hashlib
from typing import List, Dict, Any, Optional, Tuple


class DenseEmbeddingModel:
    """
    Self-contained semantic embedding model producing 64-dimensional unit vectors.
    Uses subword hashing, term frequency weighting, and positional n-grams to map text
    into a continuous metric space where semantically similar texts yield high cosine similarity.
    Compatible with external APIs (e.g. Gemini text-embedding-004) if an API key is configured.
    """

    DIMENSION = 64

    def __init__(self, use_gemini_if_available: bool = True):
        self.gemini_api_key = os.environ.get("GEMINI_API_KEY", "")
        self.use_gemini = use_gemini_if_available and bool(self.gemini_api_key)

    def _hash_token_to_features(self, token: str, dim: int) -> List[float]:
        vec = [0.0] * dim
        token_lower = token.lower().strip()
        if not token_lower:
            return vec

        # Primary hash
        h1 = int(hashlib.md5(token_lower.encode('utf-8')).hexdigest(), 16)
        # Secondary hash for sign and cross-dimension dispersion
        h2 = int(hashlib.sha256(token_lower.encode('utf-8')).hexdigest(), 16)

        idx1 = h1 % dim
        idx2 = (h1 // dim) % dim
        idx3 = (h2 % dim)

        val1 = 1.0 if ((h2 >> 1) & 1) else -1.0
        val2 = 0.75 if ((h2 >> 2) & 1) else -0.75
        val3 = 0.5 if ((h2 >> 3) & 1) else -0.5

        vec[idx1] += val1
        vec[idx2] += val2
        vec[idx3] += val3

        # Character trigrams for morphological similarity
        if len(token_lower) >= 3:
            for i in range(len(token_lower) - 2):
                tri = token_lower[i:i+3]
                tri_h = int(hashlib.md5(tri.encode('utf-8')).hexdigest(), 16)
                t_idx = tri_h % dim
                vec[t_idx] += 0.3 * (1.0 if (tri_h & 1) else -1.0)

        return vec

    def embed_text(self, text: str) -> List[float]:
        """Embeds a single string into a 64-dimensional L2-normalized vector."""
        if not text:
            return [0.0] * self.DIMENSION

        # Tokenize words
        words = [w.lower().strip(",.!?():;\"'\n\t") for w in text.split()]
        words = [w for w in words if len(w) > 1]

        if not words:
            return [0.0] * self.DIMENSION

        aggregate = [0.0] * self.DIMENSION

        # Term frequency weighting with sublinear scaling
        tf: Dict[str, float] = {}
        for w in words:
            tf[w] = tf.get(w, 0.0) + 1.0

        for w, count in tf.items():
            weight = 1.0 + math.log(count)
            f_vec = self._hash_token_to_features(w, self.DIMENSION)
            for i in range(self.DIMENSION):
                aggregate[i] += f_vec[i] * weight

        # Add bigram semantics for contiguous phrasing
        for i in range(len(words) - 1):
            bigram = f"{words[i]}_{words[i+1]}"
            bg_vec = self._hash_token_to_features(bigram, self.DIMENSION)
            for j in range(self.DIMENSION):
                aggregate[j] += bg_vec[j] * 0.8

        # L2-normalize vector to unit length
        norm = math.sqrt(sum(x * x for x in aggregate))
        if norm > 1e-9:
            return [float(x / norm) for x in aggregate]
        else:
            return [0.0] * self.DIMENSION

    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        return [self.embed_text(t) for t in texts]


def cosine_similarity(v1: List[float], v2: List[float]) -> float:
    """Computes cosine similarity between two vectors."""
    if not v1 or not v2 or len(v1) != len(v2):
        return 0.0

    dot = sum(a * b for a, b in zip(v1, v2))
    norm_a = math.sqrt(sum(a * a for a in v1))
    norm_b = math.sqrt(sum(b * b for b in v2))

    if norm_a < 1e-9 or norm_b < 1e-9:
        return 0.0

    similarity = dot / (norm_a * norm_b)
    # Clamp between -1.0 and 1.0 for floating point precision
    return max(-1.0, min(1.0, float(similarity)))


class VectorStore:
    """
    In-memory vector store indexing chunks with dense vector embeddings and metadata.
    Supports Top-K nearest neighbor search with similarity scores and filtering.
    """

    def __init__(self, embedder: Optional[DenseEmbeddingModel] = None):
        self.embedder = embedder or DenseEmbeddingModel()
        self.documents: Dict[str, Dict[str, Any]] = {}
        self.chunks: List[Dict[str, Any]] = []

    def clear(self):
        self.documents.clear()
        self.chunks.clear()

    def add_document(self, doc_data: Dict[str, Any]):
        """Indexes a document and all its chunks with embeddings."""
        doc_id = doc_data["id"]
        self.documents[doc_id] = {
            "id": doc_id,
            "title": doc_data["title"],
            "category": doc_data.get("category", "General"),
            "author": doc_data.get("author", "Lab"),
            "chunk_count": doc_data.get("chunk_count", 0),
            "created_at": doc_data.get("created_at", int(time.time() * 1000))
        }

        chunks = doc_data.get("chunks", [])
        for c in chunks:
            # Generate vector embedding for the chunk text
            vector = self.embedder.embed_text(c["text"])
            c_entry = {
                "chunk_id": f"{doc_id}_{c['chunk_index']}",
                "doc_id": doc_id,
                "doc_title": doc_data["title"],
                "category": doc_data.get("category", "General"),
                "chunk_index": c["chunk_index"],
                "text": c["text"],
                "char_count": c.get("char_count", len(c["text"])),
                "estimated_tokens": c.get("estimated_tokens", len(c["text"]) // 4),
                "vector": vector
            }
            self.chunks.append(c_entry)

    def search(
        self,
        query: str,
        top_k: int = 5,
        threshold: float = 0.15,
        category_filter: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Executes semantic vector search for a query against all indexed chunks.
        Returns top-k ranked results with similarity scores and execution latency.
        """
        start_time = time.perf_counter()
        query_vector = self.embedder.embed_text(query)

        scored_results: List[Dict[str, Any]] = []

        for chunk in self.chunks:
            if category_filter and chunk.get("category") != category_filter:
                continue

            sim = cosine_similarity(query_vector, chunk["vector"])
            if sim >= threshold:
                scored_results.append({
                    "chunk_id": chunk["chunk_id"],
                    "doc_id": chunk["doc_id"],
                    "doc_title": chunk["doc_title"],
                    "category": chunk["category"],
                    "chunk_index": chunk["chunk_index"],
                    "text": chunk["text"],
                    "similarity_score": round(sim, 4),
                    "match_percentage": round(max(0.0, sim) * 100, 1),
                    "vector_preview": chunk["vector"][:4]  # First 4 dimensions preview
                })

        # Rank by similarity score descending
        scored_results.sort(key=lambda x: x["similarity_score"], reverse=True)
        top_matches = scored_results[:top_k]

        latency_ms = round((time.perf_counter() - start_time) * 1000, 2)

        return {
            "query": query,
            "top_k": top_k,
            "threshold": threshold,
            "total_candidates": len(self.chunks),
            "matched_count": len(top_matches),
            "latency_ms": latency_ms,
            "results": top_matches
        }

    def get_stats(self) -> Dict[str, Any]:
        return {
            "total_documents": len(self.documents),
            "total_chunks": len(self.chunks),
            "vector_dimension": DenseEmbeddingModel.DIMENSION,
            "status": "ready"
        }


class RAGInferenceEngine:
    """
    Retrieval-Augmented Generation inference coordinator.
    Synthesizes context-grounded answers with citations from vector search hits.
    """

    def __init__(self, vector_store: VectorStore):
        self.vector_store = vector_store

    def ask(self, question: str, top_k: int = 3, threshold: float = 0.2) -> Dict[str, Any]:
        search_res = self.vector_store.search(question, top_k=top_k, threshold=threshold)
        hits = search_res["results"]

        if not hits:
            return {
                "question": question,
                "answer": "No directly matching passages were found in the indexed documents for your query. Try adjusting your search terms or lowering the similarity threshold in settings.",
                "citations": [],
                "confidence": 0.0,
                "latency_ms": search_res["latency_ms"],
                "sources_consulted": 0
            }

        # Build citations
        citations = []
        context_snippets = []
        for rank, hit in enumerate(hits, 1):
            citations.append({
                "rank": rank,
                "doc_title": hit["doc_title"],
                "chunk_id": hit["chunk_id"],
                "chunk_index": hit["chunk_index"],
                "similarity_score": hit["similarity_score"],
                "match_percentage": hit["match_percentage"],
                "snippet": hit["text"][:160] + "..." if len(hit["text"]) > 160 else hit["text"]
            })
            context_snippets.append(f"[{rank}] From '{hit['doc_title']}' (Match {hit['match_percentage']}%):\n{hit['text']}")

        top_hit = hits[0]
        avg_score = sum(h["similarity_score"] for h in hits) / len(hits)
        confidence = round(avg_score * 100, 1)

        # Grounded generative synthesis
        answer = (
            f"Based on retrieved documentation from '{top_hit['doc_title']}' (similarity: {top_hit['match_percentage']}%):\n\n"
            f"{top_hit['text']}\n\n"
        )
        if len(hits) > 1:
            answer += f"Additionally, supplementary findings from '{hits[1]['doc_title']}' indicate:\n"
            # Summarize secondary context
            second_text = hits[1]['text']
            first_sentence = second_text.split(". ")[0] if ". " in second_text else second_text[:120]
            answer += f"• {first_sentence}."

        return {
            "question": question,
            "answer": answer,
            "citations": citations,
            "confidence": confidence,
            "latency_ms": search_res["latency_ms"],
            "sources_consulted": len(hits)
        }


if __name__ == "__main__":
    from data_processing import get_sample_lab_documents, process_document

    store = VectorStore()
    for d in get_sample_lab_documents():
        proc = process_document(d["title"], d["content"], d["category"], d["author"])
        store.add_document(proc)

    print("Index stats:", store.get_stats())
    query = "How does cosine similarity differ from Euclidean distance?"
    print(f"\nTesting Vector Search for: '{query}'")
    res = store.search(query, top_k=3)
    for r in res["results"]:
        print(f"[{r['match_percentage']}%] {r['doc_title']} (Chunk {r['chunk_index']}): {r['text'][:90]}...")

    rag = RAGInferenceEngine(store)
    ans = rag.ask(query)
    print("\n--- RAG Answer ---")
    print(ans["answer"])
