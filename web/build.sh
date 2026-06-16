#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${IMAGE_NAME:-orbisops/web}"
IMAGE_TAG="${IMAGE_TAG:-2.0.0}"

docker build --load -t "${IMAGE_NAME}:${IMAGE_TAG}" -f ./Dockerfile .
