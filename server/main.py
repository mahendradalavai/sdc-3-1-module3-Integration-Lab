"""
main.py
FastAPI Server for Mobile Document Assistant Integration Lab
Exposes Vector Search, Document Ingestion, and RAG Inference endpoints.
"""

import time
from typing import List, Optional
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from data_processing import process_document, get_sample_lab_documents
from model_inference import VectorStore, DenseEmbeddingModel, RAGInferenceEngine

app = FastAPI(
    title="Integration Lab: Vector Search & Document Assistant API",
    description="Backend service providing document chunking, vector embeddings, and semantic RAG inference.",
    version="1.0.0"
)

# Enable CORS for local development and mobile network connectivity
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize vector engine and state
vector_store = VectorStore(embedder=DenseEmbeddingModel())
rag_engine = RAGInferenceEngine(vector_store)
start_time = time.time()


def seed_defaults():
    for doc in get_sample_lab_documents():
        processed = process_document(
            title=doc["title"],
            content=doc["content"],
            category=doc["category"],
            author=doc["author"]
        )
        vector_store.add_document(processed)


# Seed initial lab dataset
seed_defaults()


# --- Pydantic Request/Response Models ---

class SearchRequest(BaseModel):
    query: str = Field(..., description="Natural language search query")
    top_k: int = Field(default=5, ge=1, le=20, description="Max candidate chunks to return")
    threshold: float = Field(default=0.15, ge=0.0, le=1.0, description="Cosine similarity threshold")
    category: Optional[str] = Field(default=None, description="Optional category filter")


class ChatRequest(BaseModel):
    question: str = Field(..., description="User query or document question")
    top_k: int = Field(default=3, ge=1, le=10)
    threshold: float = Field(default=0.2, ge=0.0, le=1.0)


class IngestRequest(BaseModel):
    title: str = Field(..., description="Document title")
    content: str = Field(..., description="Full text content of the document")
    category: str = Field(default="Research", description="Document topic or domain")
    author: str = Field(default="Lab User", description="Author or source")
    chunk_size: int = Field(default=400, ge=100, le=2000)
    chunk_overlap: int = Field(default=80, ge=0, le=500)


# --- Endpoints ---

@app.get("/health")
@app.get("/api/health")
def get_health():
    """Health check and vector store capacity metrics."""
    stats = vector_store.get_stats()
    return {
        "status": "healthy",
        "uptime_seconds": round(time.time() - start_time, 1),
        "vector_store": stats
    }


@app.get("/api/documents")
def list_documents():
    """Returns all documents indexed in the vector store."""
    docs = list(vector_store.documents.values())
    return {
        "total": len(docs),
        "documents": docs
    }


@app.post("/api/documents/ingest")
def ingest_document(req: IngestRequest):
    """Processes, chunks, embeds, and indexes a new document into vector storage."""
    if not req.title.strip() or not req.content.strip():
        raise HTTPException(status_code=400, detail="Title and content must not be empty.")

    processed = process_document(
        title=req.title,
        content=req.content,
        category=req.category,
        author=req.author,
        chunk_size=req.chunk_size,
        chunk_overlap=req.chunk_overlap
    )
    vector_store.add_document(processed)

    return {
        "message": f"Successfully ingested and indexed '{req.title}'",
        "document_id": processed["id"],
        "chunk_count": processed["chunk_count"],
        "estimated_tokens": processed["total_tokens"]
    }


@app.post("/api/vector/search")
def search_vectors(req: SearchRequest):
    """Performs semantic vector search across indexed chunks."""
    if not req.query.strip():
        raise HTTPException(status_code=400, detail="Search query cannot be blank.")

    results = vector_store.search(
        query=req.query,
        top_k=req.top_k,
        threshold=req.threshold,
        category_filter=req.category
    )
    return results


@app.post("/api/chat/rag")
def chat_rag(req: ChatRequest):
    """Retrieval-Augmented Generation assistant endpoint."""
    if not req.question.strip():
        raise HTTPException(status_code=400, detail="Question cannot be blank.")

    return rag_engine.ask(
        question=req.question,
        top_k=req.top_k,
        threshold=req.threshold
    )


@app.post("/api/sample-data/seed")
def reseed_sample_data():
    """Resets and reloads the default integration lab documents."""
    vector_store.clear()
    seed_defaults()
    return {
        "message": "Reset and re-indexed default lab documents",
        "stats": vector_store.get_stats()
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
