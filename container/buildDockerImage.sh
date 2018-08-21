#!/usr/bin/env bash

set -e
set -u
set -o pipefail

SCRIPT_CURRENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGES_DIR="${SCRIPT_CURRENT_DIR}/images"
DEFAULT_IMAGE_PREFIX="accelerate-io/dpnt-video-"

BASE="base"

BASE_IMAGE_VERSION=$( cat "${IMAGES_DIR}/${BASE}/version.txt" | tr -d " " | tr -d "\n" )
ROOT_BASE_IMAGE_TAG="${DEFAULT_IMAGE_PREFIX}base:${BASE_IMAGE_VERSION}"
BASE_IMAGE_TAG=${ROOT_BASE_IMAGE_TAG}

echo "~~~~~~ Refreshing base image ~~~~~~"
docker build -t ${ROOT_BASE_IMAGE_TAG} "${IMAGES_DIR}/base/."

echo "Compute base image name+version"
base_image_version=$( cat "${SCRIPT_CURRENT_DIR}/images/${BASE}/version.txt" | tr -d " " | tr -d "\n" )
base_image_name="${DEFAULT_IMAGE_PREFIX}${BASE}"
base_image_tag="${base_image_name}:${base_image_version}"

echo "Make the current image the latest"
docker tag ${base_image_tag} ${base_image_name}:latest
