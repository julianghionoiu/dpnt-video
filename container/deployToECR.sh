#!/usr/bin/env bash

set -e
set -u
set -o pipefail

SCRIPT_CURRENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGES_DIR="${SCRIPT_CURRENT_DIR}/images"
DEFAULT_IMAGE_PREFIX="accelerate-io/dpnt-video-"

function die() { echo >&2 $1; exit 1; }
[ "$#" -eq 2 ] || die "Usage: $0 LANGUAGE_ID ECR_URL"
LANGUAGE_ID=$1
ECR_URL=$2

echo "Logging into AWS ECR"
`aws ecr get-login --no-include-email --region eu-west-1`

echo "Compute language specific name+version"
language_image_version=$( cat "${SCRIPT_CURRENT_DIR}/images/${LANGUAGE_ID}/version.txt" | tr -d " " | tr -d "\n" )
language_image_name="${DEFAULT_IMAGE_PREFIX}${LANGUAGE_ID}"
language_image_tag="${language_image_name}:${language_image_version}"
ecr_remote_tag="${ECR_URL}/${language_image_name}:${language_image_version}"

echo "Tag image"
docker tag ${language_image_tag} ${ecr_remote_tag}

echo "Push image to ECR"
docker push ${ecr_remote_tag}
