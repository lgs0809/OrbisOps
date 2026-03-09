import base64
import io
import json
import math
import os
import threading
import tempfile
import time
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from typing import Any

import numpy as np
import torch
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from PIL import Image
from sentence_transformers import CrossEncoder, SentenceTransformer


app = FastAPI(title="Embedding Model")

EMBEDDING_MODEL_ID = os.getenv("EMBEDDING_MODEL_ID", "Qwen/Qwen3-VL-Embedding-2B")
RERANK_MODEL_ID = os.getenv("RERANK_MODEL_ID", "Qwen/Qwen3-VL-Reranker-2B")
MODEL_DEVICE = os.getenv("MODEL_DEVICE", "auto")
EMBEDDING_DEVICE_SETTING = os.getenv("EMBEDDING_DEVICE", MODEL_DEVICE)
RERANK_DEVICE_SETTING = os.getenv("RERANK_DEVICE", MODEL_DEVICE)
EMBEDDING_DIMENSIONS = int(os.getenv("EMBEDDING_DIMENSIONS", "2048"))
EMBEDDING_NORMALIZE = os.getenv("EMBEDDING_NORMALIZE", "true").lower() == "true"
EMBEDDING_DEFAULT_INPUT_TYPE = os.getenv("EMBEDDING_DEFAULT_INPUT_TYPE", "document")
STARTUP_PRELOAD_MODELS = os.getenv("STARTUP_PRELOAD_MODELS", "true").lower() == "true"
STARTUP_PRELOAD_BACKGROUND = os.getenv("STARTUP_PRELOAD_BACKGROUND", "true").lower() == "true"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def elapsed_ms(started: float) -> int:
    return int((time.monotonic() - started) * 1000)


MODEL_STATE_LOCK = threading.RLock()
MODEL_STATES: dict[str, dict[str, Any]] = {
    "embedding": {
        "kind": "embedding",
        "model": EMBEDDING_MODEL_ID,
        "status": "not_loaded",
        "deviceSetting": EMBEDDING_DEVICE_SETTING,
        "device": None,
        "loadedAt": None,
        "updatedAt": utc_now(),
        "loadDurationMs": None,
        "lastError": None,
    },
    "rerank": {
        "kind": "rerank",
        "model": RERANK_MODEL_ID,
        "status": "not_loaded",
        "deviceSetting": RERANK_DEVICE_SETTING,
        "device": None,
        "loadedAt": None,
        "updatedAt": utc_now(),
        "loadDurationMs": None,
        "lastError": None,
    },
}


@app.on_event("startup")
def startup_preload_models() -> None:
    if not STARTUP_PRELOAD_MODELS:
        return
    if STARTUP_PRELOAD_BACKGROUND:
        threading.Thread(target=preload_all_models, name="local-rag-model-preload", daemon=True).start()
        return
    preload_all_models()


@lru_cache(maxsize=1)
def embedding_model() -> SentenceTransformer:
    device = embedding_device()
    started = time.monotonic()
    mark_model_state("embedding", "loading", device=device, lastError=None)
    try:
        model = SentenceTransformer(EMBEDDING_MODEL_ID, trust_remote_code=True, device=device)
        mark_model_state("embedding", "loaded", device=device, loadedAt=utc_now(), loadDurationMs=elapsed_ms(started), lastError=None)
        return model
    except Exception as exc:
        mark_model_state("embedding", "error", device=device, loadDurationMs=elapsed_ms(started), lastError=str(exc))
        raise


@lru_cache(maxsize=1)
def rerank_model() -> CrossEncoder:
    device = rerank_device()
    started = time.monotonic()
    mark_model_state("rerank", "loading", device=device, lastError=None)
    try:
        model = CrossEncoder(
            RERANK_MODEL_ID,
            device=device,
            model_kwargs={"trust_remote_code": True},
        )
        mark_model_state("rerank", "loaded", device=device, loadedAt=utc_now(), loadDurationMs=elapsed_ms(started), lastError=None)
        return model
    except Exception as exc:
        mark_model_state("rerank", "error", device=device, loadDurationMs=elapsed_ms(started), lastError=str(exc))
        raise


@lru_cache(maxsize=1)
def embedding_device() -> str:
    return resolve_device(EMBEDDING_DEVICE_SETTING)


@lru_cache(maxsize=1)
def rerank_device() -> str:
    return resolve_device(RERANK_DEVICE_SETTING)


def resolve_device(setting: str) -> str:
    normalized = (setting or "auto").strip().lower()
    if normalized != "auto":
        return normalized
    mps_backend = getattr(torch.backends, "mps", None)
    if mps_backend is not None and mps_backend.is_available():
        return "mps"
    if torch.cuda.is_available():
        return "cuda"
    return "cpu"


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "UP",
        "embeddingModel": EMBEDDING_MODEL_ID,
        "embeddingDimensions": EMBEDDING_DIMENSIONS,
        "rerankModel": RERANK_MODEL_ID,
        "deviceMode": MODEL_DEVICE,
        "startupPreloadModels": STARTUP_PRELOAD_MODELS,
        "startupPreloadBackground": STARTUP_PRELOAD_BACKGROUND,
        "embeddingDevice": embedding_device(),
        "rerankDevice": rerank_device(),
        "modelStates": model_state_snapshot(),
    }


@app.get("/ready")
def ready() -> Any:
    payload = readiness_payload()
    if payload["ready"]:
        return payload
    return JSONResponse(status_code=503, content=payload)


@app.post("/models/preload")
@app.post("/v1/models/preload")
async def preload_models(request: Request) -> Any:
    payload = await read_json_payload(request, required=False)
    models = normalize_preload_models((payload or {}).get("models"))
    errors = preload_all_models(models)
    result = readiness_payload()
    result["requestedModels"] = models
    if errors:
        result["errors"] = errors
        return JSONResponse(status_code=500, content=result)
    return result


@app.post("/v1/embeddings")
async def openai_embeddings(request: Request) -> dict[str, Any]:
    payload = await read_json_payload(request, required=False)
    if not payload:
        payload = {"input": ""}
    raw_input = payload.get("input")
    if raw_input is None:
        raise HTTPException(status_code=400, detail="input is required")
    inputs = raw_input if isinstance(raw_input, list) else [raw_input]
    texts = [str(item) for item in inputs]
    vectors = encode_items(texts, payload.get("input_type") or EMBEDDING_DEFAULT_INPUT_TYPE)
    return {
        "object": "list",
        "model": payload.get("model") or EMBEDDING_MODEL_ID,
        "data": [
            {"object": "embedding", "index": index, "embedding": vector}
            for index, vector in enumerate(vectors)
        ],
        "usage": {"prompt_tokens": 0, "total_tokens": 0},
    }


@app.post("/v1/multimodalembeddings")
async def multimodal_embeddings(request: Request) -> dict[str, Any]:
    payload = await read_json_payload(request, required=False)
    if not payload:
        return {
            "model": EMBEDDING_MODEL_ID,
            "embeddings": [],
            "object": "list",
            "data": [],
        }
    inputs = payload.get("inputs")
    if not isinstance(inputs, list) or not inputs:
        raise HTTPException(status_code=400, detail="inputs must be a non-empty list")

    requested_dimension = int(payload.get("output_dimension") or EMBEDDING_DIMENSIONS)
    items: list[Any] = []
    temp_paths: list[Path] = []
    try:
        for input_item in inputs:
            item, item_temp_paths = parse_multimodal_input(input_item)
            items.append(item)
            temp_paths.extend(item_temp_paths)
        vectors = encode_items(items, str(payload.get("input_type") or EMBEDDING_DEFAULT_INPUT_TYPE), requested_dimension)
        return {
            "model": payload.get("model") or EMBEDDING_MODEL_ID,
            "embeddings": vectors,
            "object": "list",
            "data": [
                {"object": "embedding", "index": index, "embedding": vector}
                for index, vector in enumerate(vectors)
            ],
        }
    finally:
        for path in temp_paths:
            path.unlink(missing_ok=True)


@app.post("/v1/rerank")
async def cohere_compatible_rerank(request: Request) -> dict[str, Any]:
    payload = await read_json_payload(request, required=False)
    if not payload:
        return {
            "id": "local-qwen-vl-rerank",
            "model": RERANK_MODEL_ID,
            "results": [],
        }
    query = str(payload.get("query") or "")
    documents = payload.get("documents")
    if not query or not isinstance(documents, list):
        raise HTTPException(status_code=400, detail="query and documents are required")

    top_n = int(payload.get("top_n") or len(documents))
    pairs = [[query, document_text(document)] for document in documents]
    scores = rerank_model().predict(pairs)
    results = []
    for index, score in enumerate(np.asarray(scores).reshape(-1)):
        raw_score = float(score)
        relevance_score = sigmoid(raw_score) if math.isfinite(raw_score) else 0.0
        row = {"index": index, "relevance_score": relevance_score}
        if payload.get("return_documents") is True:
            row["document"] = documents[index]
        results.append(row)
    results.sort(key=lambda item: item["relevance_score"], reverse=True)
    return {
        "id": "local-qwen-vl-rerank",
        "model": payload.get("model") or RERANK_MODEL_ID,
        "results": results[: max(1, min(top_n, len(results)))],
    }


async def read_json_payload(request: Request, required: bool = True) -> dict[str, Any]:
    body = await request.body()
    if not body:
        if required:
            raise HTTPException(status_code=400, detail="request body is required")
        return {}
    try:
        payload = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise HTTPException(status_code=400, detail=f"invalid JSON body: {exc}") from exc
    if isinstance(payload, str):
        try:
            payload = json.loads(payload)
        except json.JSONDecodeError as exc:
            raise HTTPException(status_code=400, detail=f"invalid JSON string body: {exc}") from exc
    if not isinstance(payload, dict):
        raise HTTPException(status_code=400, detail="request body must be a JSON object")
    return payload


def parse_multimodal_input(input_item: dict[str, Any]) -> tuple[Any, list[Path]]:
    content = input_item.get("content") if isinstance(input_item, dict) else None
    if not isinstance(content, list):
        return str(input_item), []

    text_parts: list[str] = []
    image_paths: list[Path] = []
    for content_item in content:
        if not isinstance(content_item, dict):
            continue
        item_type = str(content_item.get("type") or "").lower()
        if item_type == "text" and content_item.get("text") is not None:
            text_parts.append(str(content_item.get("text")))
        elif item_type in {"image_base64", "image"}:
            data = content_item.get("image_base64") or content_item.get("image")
            if data:
                image_paths.append(save_image_data(str(data)))

    text = "\n".join(part for part in text_parts if part.strip()).strip()
    if image_paths:
        item: dict[str, Any] = {"image": str(image_paths[0])}
        if text:
            item["text"] = text
        return item, image_paths
    return text, []


def save_image_data(value: str) -> Path:
    encoded = value.split(",", 1)[1] if value.startswith("data:") and "," in value else value
    image_bytes = base64.b64decode(encoded)
    image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    suffix = ".png"
    tmp = tempfile.NamedTemporaryFile(prefix="rag-image-", suffix=suffix, delete=False)
    tmp.close()
    path = Path(tmp.name)
    image.save(path)
    return path


def encode_items(items: list[Any], input_type: str = "document", requested_dimension: int | None = None) -> list[list[float]]:
    dimension = requested_dimension or EMBEDDING_DIMENSIONS
    model = embedding_model()
    encode_kwargs = {
        "normalize_embeddings": EMBEDDING_NORMALIZE,
        "convert_to_numpy": True,
    }
    normalized_input_type = (input_type or "document").lower()
    if normalized_input_type == "query" and hasattr(model, "encode_query"):
        vectors = model.encode_query(items, **encode_kwargs)
    elif normalized_input_type == "document" and hasattr(model, "encode_document"):
        vectors = model.encode_document(items, **encode_kwargs)
    else:
        vectors = model.encode(items, **encode_kwargs)
    return [resize_vector(vector, dimension) for vector in np.asarray(vectors)]


def resize_vector(vector: np.ndarray, dimension: int) -> list[float]:
    flat = np.asarray(vector, dtype=np.float32).reshape(-1)
    if dimension > 0 and len(flat) > dimension:
        flat = flat[:dimension]
        norm = np.linalg.norm(flat)
        if EMBEDDING_NORMALIZE and norm > 0:
            flat = flat / norm
    if dimension > 0 and len(flat) != dimension:
        raise HTTPException(status_code=500, detail=f"embedding dimension mismatch: expected={dimension}, actual={len(flat)}")
    return [float(value) for value in flat]


def document_text(document: Any) -> str:
    if isinstance(document, dict):
        return str(document.get("text") or document.get("content") or "")
    return str(document)


def sigmoid(value: float) -> float:
    if value >= 0:
        z = math.exp(-value)
        return 1 / (1 + z)
    z = math.exp(value)
    return z / (1 + z)


def preload_all_models(models: list[str] | None = None) -> dict[str, str]:
    requested_models = models or ["embedding", "rerank"]
    errors: dict[str, str] = {}
    for model_name in requested_models:
        try:
            if model_name == "embedding":
                embedding_model()
            elif model_name == "rerank":
                rerank_model()
            else:
                errors[model_name] = "unknown model kind"
        except Exception as exc:
            errors[model_name] = str(exc)
    return errors


def mark_model_state(kind: str, status: str, **fields: Any) -> None:
    with MODEL_STATE_LOCK:
        state = MODEL_STATES[kind]
        state["status"] = status
        state["updatedAt"] = utc_now()
        for key, value in fields.items():
            state[key] = value


def model_state_snapshot() -> dict[str, dict[str, Any]]:
    with MODEL_STATE_LOCK:
        snapshot = {kind: dict(state) for kind, state in MODEL_STATES.items()}
    snapshot["embedding"]["device"] = snapshot["embedding"].get("device") or embedding_device()
    snapshot["rerank"]["device"] = snapshot["rerank"].get("device") or rerank_device()
    return snapshot


def readiness_payload() -> dict[str, Any]:
    states = model_state_snapshot()
    ready_state = all(state["status"] == "loaded" for state in states.values())
    return {
        "status": "READY" if ready_state else "NOT_READY",
        "ready": ready_state,
        "embeddingModel": EMBEDDING_MODEL_ID,
        "embeddingDimensions": EMBEDDING_DIMENSIONS,
        "rerankModel": RERANK_MODEL_ID,
        "deviceMode": MODEL_DEVICE,
        "modelStates": states,
    }


def normalize_preload_models(value: Any) -> list[str]:
    if value is None:
        return ["embedding", "rerank"]
    if isinstance(value, str):
        return [value.strip().lower()]
    if isinstance(value, list):
        return [str(item).strip().lower() for item in value if str(item).strip()]
    raise HTTPException(status_code=400, detail="models must be a string or list")
