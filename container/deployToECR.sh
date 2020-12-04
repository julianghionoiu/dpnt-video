#!/usr/bin/env bash

set -e
set -u
set -o pipefail

SCRIPT_CURRENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGES_DIR="${SCRIPT_CURRENT_DIR}/images"
DEFAULT_IMAGE_PREFIX="accelerate-io/dpnt-video-"
BASE="base"

function die() { echo >&2 $1; exit 1; }
[ "$#" -eq 1 ] || die "Usage: $0 ECR_URL"
ECR_URL=$1

echo "Logging into AWS ECR"
`aws ecr get-login --no-include-email --region eu-west-1`

echo "Compute base name+version"
base_image_version=$( cat "${SCRIPT_CURRENT_DIR}/images/${BASE}/version.txt" | tr -d " " | tr -d "\n" )
base_image_name="${DEFAULT_IMAGE_PREFIX}${BASE}"
base_image_tag="${base_image_name}:${base_image_version}"
ecr_remote_tag="${ECR_URL}/${base_image_name}:${base_image_version}"

echo "Tag image"
docker tag ${base_image_tag} ${ecr_remote_tag}

echo "Push image to ECR"
docker push ${ecr_remote_tag}
