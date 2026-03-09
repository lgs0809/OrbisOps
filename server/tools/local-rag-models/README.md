# Shared Local RAG Models Service

This subproject packages the local embedding and rerank models as a Docker-level shared service.
The default runtime is a fixed-name Docker container shared by every local project. A native
Conda backend remains available explicitly for Apple Silicon MPS.

```text
Qwen/Qwen3-VL-Embedding-2B -> /v1/embeddings, /v1/multimodalembeddings
Qwen/Qwen3-VL-Reranker-2B  -> /v1/rerank
FastAPI + Uvicorn          -> http://127.0.0.1:8110
```

## Runtime Endpoints

- `GET /health`: process health, resolved device and model load state.
- `GET /ready`: readiness check, returns `503` until startup loading finishes.
- `POST /models/preload`: manually reload/check `embedding` and `rerank` model readiness.
- `POST /v1/embeddings`: OpenAI-compatible text embedding endpoint.
- `POST /v1/multimodalembeddings`: multimodal embedding endpoint for text plus base64 image input.
- `POST /v1/rerank`: Cohere-compatible rerank endpoint.

## Setup

```bash
make local-rag-models-setup
```

This builds the shared Docker image directly from this project. The default resources are:

```text
image: embedding-model:local
container: embedding-model
volume: embedding-model-cache
```

## Start

```bash
make local-rag-models-up
```

The command first validates and reuses a compatible service already listening on port `8110`.
Concurrent project startups converge on the fixed Docker container name, so only one container
is created. Docker stores model files in the named volume `embedding-model-cache`. Use
`docker logs -f embedding-model` for logs.

Other projects do not call scripts from this repository. Their startup scripts directly probe
`http://127.0.0.1:8110/ready` and inspect/start the fixed `embedding-model` Docker container.
They can reuse the same local image, or pull an identically versioned image from a shared registry.

`/health` only checks that the HTTP process is alive. The service starts loading both embedding and rerank models immediately on startup; `/ready` returns `200` only after both models are loaded.

The startup script waits for `/ready` by default. First startup can take several minutes if HuggingFace model files are not cached yet. To only wait for HTTP health during local debugging:

```bash
LOCAL_RAG_WAIT_READY=false make local-rag-models-up
```

Startup preload is enabled by default through `LOCAL_RAG_STARTUP_PRELOAD_MODELS=true`. Keep it enabled for normal development and application integration so the first business request does not pay model cold-start latency.

Startup keeps one rotated log copy at `logs/local-rag-models.log.1` when the current log exceeds `LOCAL_RAG_LOG_MAX_BYTES` bytes. The default threshold is 10 MiB.

On Apple Silicon, the default device mode is `auto`, which picks PyTorch MPS when available. To force a device:

```bash
bash scripts/setup-local-rag-models-conda.sh
LOCAL_RAG_MODEL_DEVICE=mps bash scripts/ensure-local-rag-models.sh
LOCAL_RAG_MODEL_DEVICE=cpu bash scripts/ensure-local-rag-models.sh
```

## Stop

```bash
make local-rag-models-down
```

This is an explicit global stop. Stopping `orbisops` does not stop the shared model
service unless `STOP_LOCAL_RAG_MODELS=true` is supplied.

## Implementation Basis

- Embedding model loading follows the official `Qwen/Qwen3-VL-Embedding-2B` HuggingFace model card with `sentence-transformers`.
- Rerank model loading follows the official `Qwen/Qwen3-VL-Reranker-2B` HuggingFace model card with `CrossEncoder`.
- Apple Silicon acceleration uses the official PyTorch MPS backend. The startup script enables `PYTORCH_ENABLE_MPS_FALLBACK=1` so unsupported ops can fall back instead of failing the whole service.

References:

- https://huggingface.co/Qwen/Qwen3-VL-Embedding-2B
- https://huggingface.co/Qwen/Qwen3-VL-Reranker-2B
- https://docs.pytorch.org/docs/stable/notes/mps.html
