#!/usr/bin/env bash

set -e
set -u
set -o pipefail

SCRIPT_CURRENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGES_DIR="${SCRIPT_CURRENT_DIR}/images"
BASE="base"
export DEBUG="${DEBUG:-}"

function die() { echo >&2 $1; exit 1; }
[ "$#" -eq 2 ] || die "Usage: $0 PARTICIPANT_ID CHALLENGE_ID"
PARTICIPANT_ID=$1
CHALLENGE_ID=$2

echo "Compute base image name+version"
DEFAULT_IMAGE_PREFIX="accelerate-io/dpnt-video-"
base_image_version=$( cat "${IMAGES_DIR}/${BASE}/version.txt" | tr -d " " | tr -d "\n" )
base_image_name="${DEFAULT_IMAGE_PREFIX}${BASE}"
base_image_tag="${base_image_name}:${base_image_version}"

echo "Running ${base_image_tag} from the local docker registry"
DOCKER_DEBUG_PARAMS=""
if [[ "${DEBUG}" == "true" ]]; then
    DOCKER_DEBUG_PARAMS="--interactive --tty --entrypoint /bin/bash --volume ${SCRIPT_CURRENT_DIR}:/debug-repo"
    echo "*************************"
    echo "* Running in Debug mode *"
    echo "*************************"
fi

docker run                                                                      \
      ${DOCKER_DEBUG_PARAMS}                                                    \
      --env AWS_ACCESS_KEY_ID=unused                                            \
      --env AWS_SECRET_ACCESS_KEY=unused                                        \
      --env S3_ENDPOINT=unused                                                  \
      --env S3_REGION=unused                                                    \
      --env SQS_ENDPOINT=unused                                                 \
      --env SQS_REGION=unused                                                   \
      --env SQS_QUEUE_URL=unused                                                \
      --env PARTICIPANT_ID=${PARTICIPANT_ID}                                    \
      --env CHALLENGE_ID=${CHALLENGE_ID}                                        \
      ${base_image_tag}
