#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${IMAGE_NAME:-orbisops/server}"
IMAGE_TAG="${IMAGE_TAG:-2.0.0}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

docker build --load -t "${IMAGE_NAME}:${IMAGE_TAG}" -f "${SCRIPT_DIR}/Dockerfile" "${SERVER_ROOT}"
