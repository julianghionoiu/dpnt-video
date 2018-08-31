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
ensure_env "S3_URL_NEW_VIDEO"
ensure_env "S3_URL_ACCUMULATOR_VIDEO"

echo  "~~~~~~ Download and merge video into accumulator ~~~~~~" > /dev/null
TARGET_VIDEO_NAME="real-recording.mp4"
VIDEOS_LIST="mylist.txt"
echo  "Downloading new screencast and the accumulator videos from the s3 bucket"
NEW_VIDEO=$(echo ${S3_URL_NEW_VIDEO##*/})
aws s3 cp "${S3_URL_NEW_VIDEO}" . --endpoint ${S3_ENDPOINT}
aws s3 ls ${S3_URL_ACCUMULATOR_VIDEO} --endpoint ${S3_ENDPOINT} && \
          aws s3 cp "${S3_URL_ACCUMULATOR_VIDEO}" . --endpoint ${S3_ENDPOINT} || true

rm ${VIDEOS_LIST} || true
if [[ -e "${TARGET_VIDEO_NAME}" ]]; then
   echo "file '${TARGET_VIDEO_NAME}'" > ${VIDEOS_LIST}
   ls screencast_* -1 | xargs -n1 -I {} echo "file '{}'" >> ${VIDEOS_LIST}

   echo  "Merging '${S3_URL_NEW_VIDEO}' into '${S3_URL_ACCUMULATOR_VIDEO}'"
   cat ${VIDEOS_LIST}
   ffmpeg -f concat -safe 0 -i ${VIDEOS_LIST} -c copy ${S3_URL_ACCUMULATOR_VIDEO}
else
   cp ${NEW_VIDEO} ${TARGET_VIDEO_NAME}
fi

echo  "Uploading the the merged video to '${S3_URL_ACCUMULATOR_VIDEO}'"
MERGED_ACCUMULATOR_VIDEO=$(ls ${TARGET_VIDEO_NAME} -1)
aws s3 cp ${MERGED_ACCUMULATOR_VIDEO} "${S3_URL_ACCUMULATOR_VIDEO}" --endpoint ${S3_ENDPOINT}

echo  "~~~~~~ Publish results ~~~~~~" > /dev/null
merged_video_status="$(cat ${VIDEOS_LIST} | wc -l) video(s) merged into ${MERGED_ACCUMULATOR_VIDEO}"
##TODO check minio to see if we can get a http url & aws s3 - see docs
VIDEO_LINK="${S3_URL_ACCUMULATOR_VIDEO}"

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
