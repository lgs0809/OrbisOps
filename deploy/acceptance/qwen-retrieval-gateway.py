#!/usr/bin/env python3
"""Local text-only gateway for the agreed Qwen retrieval models.

No weights are downloaded. Supply local Hugging Face snapshot directories whose
names match the pinned revisions. This script has no mock/fallback mode. It is
not started by the default acceptance stack when the real models are absent.
"""
import argparse
import json
import math
import os
import re
import threading
import unicodedata
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

EMBEDDING = 'Qwen/Qwen3-VL-Embedding-2B'
RERANKER = 'Qwen/Qwen3-VL-Reranker-2B'
PREPROCESS = 'skill-route-text-v1:nfc:instruction:mrl1024:l2'
QUERY_INSTRUCTION = 'Retrieve relevant operational procedures for the query.'
DOCUMENT_INSTRUCTION = "Represent the user's input."


def snapshot(value, revision):
    directory = Path(value).expanduser().resolve()
    if not re.fullmatch(r'[a-f0-9]{40}', revision) or directory.name != revision:
        raise ValueError('A local snapshot directory matching the pinned commit is required')
    if not (directory / 'config.json').is_file():
        raise ValueError('Local model snapshot is missing config.json')
    return directory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--embedding-dir', required=True)
    parser.add_argument('--embedding-revision', required=True)
    parser.add_argument('--reranker-dir', required=True)
    parser.add_argument('--reranker-revision', required=True)
    parser.add_argument('--port', type=int, default=18863)
    parser.add_argument('--device', default='cpu')
    args = parser.parse_args()
    embedding_dir = snapshot(args.embedding_dir, args.embedding_revision)
    reranker_dir = snapshot(args.reranker_dir, args.reranker_revision)
    os.environ['HF_HUB_OFFLINE'] = '1'
    os.environ['TRANSFORMERS_OFFLINE'] = '1'
    from sentence_transformers import SentenceTransformer, CrossEncoder
    # The official model cards document this API. MRL truncation is followed by
    # explicit L2 normalization; scorer logits remain unmodified.
    embedder = SentenceTransformer(str(embedding_dir), device=args.device, local_files_only=True,
                                  trust_remote_code=False, truncate_dim=1024)
    reranker = CrossEncoder(str(reranker_dir), device=args.device, local_files_only=True,
                            trust_remote_code=False)
    semaphore = threading.BoundedSemaphore(1)
    api_key = os.environ.get('ORBISOPS_RETRIEVAL_API_KEY', '')

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt, *values):
            # No prompts, documents, Authorization headers or model keys in logs.
            return

        def respond(self, code, value):
            body = json.dumps(value, ensure_ascii=False, allow_nan=False).encode()
            self.send_response(code)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            self.connection.settimeout(6)
            if api_key and self.headers.get('Authorization') != 'Bearer ' + api_key:
                self.respond(401, {'error': 'UNAUTHORIZED'})
                return
            length = int(self.headers.get('Content-Length', '0'))
            if length < 1 or length > 128000:
                self.respond(413, {'error': 'INPUT_SIZE_INVALID'})
                return
            if not semaphore.acquire(blocking=False):
                self.respond(503, {'error': 'RETRIEVAL_BUSY'})
                return
            try:
                data = json.loads(self.rfile.read(length))
                if data.get('preprocessing') != PREPROCESS:
                    raise ValueError('PREPROCESSING_MISMATCH')
                if self.path == '/embed':
                    if data.get('model') != EMBEDDING or data.get('revision') != args.embedding_revision:
                        raise ValueError('MODEL_IDENTITY_MISMATCH')
                    if data.get('dimensions') != 1024 or data.get('kind') not in ('query', 'document'):
                        raise ValueError('EMBEDDING_CONTRACT_INVALID')
                    text = unicodedata.normalize('NFC', data['text'])
                    instruction = QUERY_INSTRUCTION if data['kind'] == 'query' else DOCUMENT_INSTRUCTION
                    result = embedder.encode([text], prompt=instruction, truncate_dim=1024,
                                             normalize_embeddings=True)[0].tolist()
                    norm = math.sqrt(sum(float(x) ** 2 for x in result))
                    if len(result) != 1024 or not math.isfinite(norm) or norm == 0:
                        raise ValueError('EMBEDDING_INVALID')
                    self.respond(200, {'model': EMBEDDING, 'revision': args.embedding_revision,
                                       'preprocessing': PREPROCESS, 'embedding': [x / norm for x in result]})
                elif self.path == '/rerank':
                    if data.get('model') != RERANKER or data.get('revision') != args.reranker_revision:
                        raise ValueError('MODEL_IDENTITY_MISMATCH')
                    documents = data['documents']
                    if not 1 <= len(documents) <= 20 or len({d['id'] for d in documents}) != len(documents):
                        raise ValueError('RERANK_CANDIDATES_INVALID')
                    query = unicodedata.normalize('NFC', data['query'])
                    scores = reranker.predict([(query, unicodedata.normalize('NFC', d['text'])) for d in documents],
                                              prompt=QUERY_INSTRUCTION).tolist()
                    if len(scores) != len(documents) or not all(math.isfinite(float(s)) for s in scores):
                        raise ValueError('RERANK_RESULT_INVALID')
                    self.respond(200, {'model': RERANKER, 'revision': args.reranker_revision,
                                       'preprocessing': PREPROCESS,
                                       'ranking': [{'id': d['id'], 'score': float(s)} for d, s in zip(documents, scores)]})
                else:
                    self.respond(404, {'error': 'UNKNOWN_ENDPOINT'})
            except (ValueError, KeyError, TypeError):
                self.respond(400, {'error': 'RETRIEVAL_CONTRACT_INVALID'})
            except Exception:
                self.respond(503, {'error': 'MODEL_INFERENCE_UNAVAILABLE'})
            finally:
                semaphore.release()

    ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()


if __name__ == '__main__':
    main()
