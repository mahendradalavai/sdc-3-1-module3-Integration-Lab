# DocVector Assistant

A high-performance mobile document assistant with vector search capabilities, semantic document retrieval, and server-side Python data processing and model inference pipelines.

---

## Overview

DocVector Assistant enables intelligent document management, semantic vector search, and Retrieval-Augmented Generation (RAG) directly on Android devices with seamless integration to an optional high-performance Python inference backend.

### Architecture Highlights

- **Mobile Client (Android)**:
  - **Language & Framework**: Kotlin with Jetpack Compose (Material 3).
  - **Local Persistence & Search**: Room Database persisting documents, semantic chunks, and vector embeddings.
  - **On-Device Vector Engine**: Pure Kotlin 64-dimensional dense embedding generation and cosine similarity calculation. Enables instant offline semantic document discovery.
  - **Network Layer**: Retrofit 2 + Moshi for real-time synchronization with the inference server.
  - **Interactive Interfaces**:
    - **Vector Search Lab**: Real-time semantic search with similarity threshold filtering and multi-dimensional vector inspection.
    - **Grounding Chat Assistant**: Conversational Q&A with verifiable grounding citations and chunk inspection.
    - **Document Library**: Full document ingestion pipeline with chunk previews and vector computation.
    - **Server Diagnostics**: Latency ping, vector capacity monitoring, and local/remote engine switching.

- **Backend Inference Service (`/server`)**:
  - **FastAPI / Python**: REST API service handling document ingestion, recursive text chunking, dense embedding inference, and cosine similarity ranking.
  - **Pipeline Modules**:
    - `data_processing.py`: Text normalization, token estimation, and recursive overlapping chunking.
    - `model_inference.py`: Dense vector embedding generation, vector indexing, and grounded RAG answer synthesis.
    - `main.py`: Production-grade REST API server with interactive OpenAPI docs (`/docs`).

---

## Project Structure

```
├── app/                                # Android Application Module
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt             # Edge-to-edge Compose entry point
│   │   ├── data/
│   │   │   ├── local/                  # Room Database, DAOs, & Entities
│   │   │   ├── model/                  # Domain Models & Network DTOs
│   │   │   ├── remote/                 # Retrofit API & Client configuration
│   │   │   ├── repository/             # Unified Document & Vector Search Repository
│   │   │   └── vector/                 # On-device Embedding Generator & Vector Math
│   │   └── ui/
│   │       ├── DocumentAssistantViewModel.kt
│   │       ├── components/             # Reusable M3 badges, cards & vector visualizers
│   │       ├── screens/                # VectorSearch, Chat, Library & Diagnostics screens
│   │       └── theme/                  # DocVector Design System & Color Palette
│   └── src/test/                       # Unit tests & Roborazzi screenshot verification
├── server/                             # Python Server-Side Inference & Ingestion
│   ├── data_processing.py              # Text cleaning & recursive chunking
│   ├── model_inference.py              # Dense vector embeddings & RAG synthesis
│   ├── main.py                         # FastAPI REST application
│   └── requirements.txt                # Python backend dependencies
└── settings.gradle.kts                 # Project settings & module definitions
```

---

## Getting Started

### Prerequisites

- **Android Development**: Android Studio Jellyfish / Ladybug or newer with Android SDK 36, JDK 17+.
- **Server Backend**: Python 3.10+ and `pip`.

### 1. Running the Python Inference Server

```bash
cd server
pip install -r requirements.txt
python main.py
```

The backend server starts on `http://localhost:8000`. You can inspect the endpoints via Swagger UI at `http://localhost:8000/docs`.

### 2. Building and Running the Android App

1. Open the project root in **Android Studio**.
2. Allow Gradle to sync dependencies.
3. Build the project:
   ```bash
   gradle assembleDebug
   ```
4. Run unit and screenshot tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
5. Deploy to a connected device or Android Emulator.
   - When connecting from the Android Emulator to the host Python server, configure the server host in the app's **Lab Server** tab to:
     `http://10.0.2.2:8000`

---

## Key Features

1. **Dual Search Modes**:
   - **Local On-Device**: Fully functional offline vector search utilizing local Room database and on-device cosine similarity calculation.
   - **Remote Server**: Offloads document vector calculations and RAG synthesis to the Python FastAPI backend.
2. **Transparent Vector Inspection**:
   - View dense 64-dimensional float vector representations directly within the UI.
   - Detailed similarity scoring badges (High / Moderate / Low) with percentage indicators.
3. **Evidence-Grounded Assistant**:
   - Every answer links directly to document source chunks with citation chips for verification.
