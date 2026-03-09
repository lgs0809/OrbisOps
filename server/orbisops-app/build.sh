#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${IMAGE_NAME:-orbisops/server}"
IMAGE_TAG="${IMAGE_TAG:-0.1.0}"

docker build --load -t "${IMAGE_NAME}:${IMAGE_TAG}" -f ./Dockerfile .
