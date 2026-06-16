#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${IMAGE_NAME:-orbisops/web}"
IMAGE_TAG="${IMAGE_TAG:-2.0.0}"
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"

docker buildx build --platform "${PLATFORMS}" -t "${IMAGE_NAME}:${IMAGE_TAG}" --push .
