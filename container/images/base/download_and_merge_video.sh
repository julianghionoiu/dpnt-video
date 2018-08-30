#!/usr/bin/env bash

set -x
set -e
set -u
set -o pipefail

SCRIPT_CURRENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# To check if an ENV variable exists, we dereference the input string $1 -> !1
# then then we attempt parameter expansion with the "+x" string
# it should return an empty string if ENV not defined
# Reference: https://stackoverflow.com/questions/3601515/how-to-check-if-a-variable-is-set-in-bash
function ensure_env {
    if [ -z "${!1+x}" ]; then echo "Environment variable $1 not set"; exit 1; fi
}

WORK_DIR=${SCRIPT_CURRENT_DIR}

ensure_env "AWS_ACCESS_KEY_ID"
ensure_env "AWS_SECRET_ACCESS_KEY"

ensure_env "S3_ENDPOINT"
ensure_env "S3_REGION"

ensure_env "SQS_ENDPOINT"
ensure_env "SQS_REGION"
ensure_env "SQS_QUEUE_URL"

ensure_env "PARTICIPANT_ID"
ensure_env "CHALLENGE_ID"

echo  "~~~~~~ Download and merge video into accumulator ~~~~~~" > /dev/null
TARGET_VIDEO_NAME="real-recording.mp4"
BUCKET_NAME="tdl-official-videos"
S3_BUCKET_URL="s3://${BUCKET_NAME}/${CHALLENGE_ID}/${PARTICIPANT_ID}"
VIDEOS_LIST="mylist.txt"
echo  "Downloading all screencasts from the s3 bucket '${BUCKET_NAME}'"
aws s3 cp "${S3_BUCKET_URL}" . --recursive --exclude "*" --include "screencast_*" --endpoint ${S3_ENDPOINT}
rm ${VIDEOS_LIST} || true
ls screencast_* -1 | xargs -n1 -I {} echo "file '{}'" >> mylist.txt

echo  "Merging all downloaded screencasts into '${TARGET_VIDEO_NAME}'"
cat ${VIDEOS_LIST}
rm ${TARGET_VIDEO_NAME} || true
ffmpeg -f concat -safe 0 -i ${VIDEOS_LIST} -c copy ${TARGET_VIDEO_NAME}

echo  "Uploading the '${TARGET_VIDEO_NAME}' video into the s3 bucket '${BUCKET_NAME}'"
aws s3 cp ${TARGET_VIDEO_NAME} "${S3_BUCKET_URL}/${TARGET_VIDEO_NAME}" --endpoint ${S3_ENDPOINT}

echo  "~~~~~~ Publish results ~~~~~~" > /dev/null
merged_video_status="$(cat ${VIDEOS_LIST} | wc -l) video(s) merged into ${TARGET_VIDEO_NAME}"
VIDEO_LINK="${S3_BUCKET_URL}/${TARGET_VIDEO_NAME}"

if [[ "${SQS_QUEUE_URL}" != http* ]]; then
    echo "SQS_QUEUE_URL does not seem to be valid. Will print to the console and exit" > /dev/null
    echo "participant=${PARTICIPANT_ID} challengeId=${CHALLENGE_ID} videoLink=${VIDEO_LINK}"
    exit 0
fi

echo "Publish video to interop event queue" > /dev/null
INTEROP_QUEUE_CONFIG="${WORK_DIR}/sqs_queue.conf"
cat > ${INTEROP_QUEUE_CONFIG} <<EOL
sqs {
  serviceEndpoint = "${SQS_ENDPOINT}"
  signingRegion = "${SQS_REGION}"
  queueUrl = "${SQS_QUEUE_URL}"
}
EOL
cat ${INTEROP_QUEUE_CONFIG}
DRY_RUN=false java -Dconfig.file="${INTEROP_QUEUE_CONFIG}" \
    -jar "${WORK_DIR}/queue-cli-tool-all.jar" \
    send rawVideoUpdated \
    participant=${PARTICIPANT_ID} challengeId=${CHALLENGE_ID} videoLink="${VIDEO_LINK}"
# Output: Public URL published to SQS or printed on the console
