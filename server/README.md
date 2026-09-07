# Integration Lab: Server-Side Data Processing & Vector Search

This folder contains the server-side Python components for the **Mobile Document Assistant**:
1. `data_processing.py`: Document ingestion, text cleaning, and recursive token-aware chunking.
2. `model_inference.py`: 64-dimensional dense vector embeddings, cosine similarity vector index, and RAG inference synthesis.
3. `main.py`: FastAPI server exposing REST API endpoints for the Android client.

---

## Quick Start

### 1. Install Dependencies
```bash
pip install -r requirements.txt
```

### 2. Run the Server
```bash
python main.py
# Or using uvicorn directly:
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```
The server will start at `http://localhost:8000`.
Interactive Swagger UI documentation is available at `http://localhost:8000/docs`.

---

## Android Mobile Integration
- When testing on the **Android Emulator**, use `http://10.0.2.2:8000` as the backend URL (since `10.0.2.2` maps to the host machine's `localhost`).
- The mobile app features a dedicated **Lab Server Status** tab where you can configure the backend URL, verify connection health, and trigger remote vector search and RAG synthesis.
- The mobile app also includes an **On-Device Vector Search Engine** with Room database persistence that functions seamlessly offline or when the server is disconnected!

---

## Key API Endpoints
- `GET /health`: Health check and vector store stats.
- `GET /api/documents`: List of indexed documents.
- `POST /api/documents/ingest`: Ingest, recursively chunk, embed, and index a new document.
- `POST /api/vector/search`: Semantic vector search query -> ranked chunks with similarity scores.
- `POST /api/chat/rag`: Question answering grounded in indexed document chunks with citations.
- `POST /api/sample-data/seed`: Re-seed the initial lab dataset.
