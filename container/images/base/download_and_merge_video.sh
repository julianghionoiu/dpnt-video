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
if [[ -s "${TARGET_VIDEO_NAME}" ]]; then
   echo "file '${TARGET_VIDEO_NAME}'" > ${VIDEOS_LIST}
   echo "file '${NEW_VIDEO}'" >> ${VIDEOS_LIST}

   echo  "Concatenating '${NEW_VIDEO}' at the bottom of '${TARGET_VIDEO_NAME}'"
   cat ${VIDEOS_LIST}
   ffmpeg -f concat -safe 0 -i ${VIDEOS_LIST} -c copy "new_${TARGET_VIDEO_NAME}"
   rm ${TARGET_VIDEO_NAME}
   mv "new_${TARGET_VIDEO_NAME}" ${TARGET_VIDEO_NAME}
else
   echo  "Copying '${NEW_VIDEO}' into '${TARGET_VIDEO_NAME}' as accumulator video does not pre-exist"
   cp ${NEW_VIDEO} ${TARGET_VIDEO_NAME}
fi

echo  "Uploading the merged video to '${S3_URL_ACCUMULATOR_VIDEO}'"
aws s3 cp ${TARGET_VIDEO_NAME} "${S3_URL_ACCUMULATOR_VIDEO}" --acl public-read --endpoint ${S3_ENDPOINT}

echo  "~~~~~~ Publish results ~~~~~~" > /dev/null
BUCKET_AND_KEY=$(echo ${S3_URL_ACCUMULATOR_VIDEO} | cut -c6-) # remove the prefix s3://
VIDEO_LINK="${S3_ENDPOINT}/${BUCKET_AND_KEY}"

if [[ "${SQS_QUEUE_URL}" != http* ]]; then
    echo "SQS_QUEUE_URL does not seem to be valid. Will print to the console and exit" > /dev/null
    echo "participant=${PARTICIPANT_ID} challengeId=${CHALLENGE_ID} videoLink=\"${VIDEO_LINK}\""
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
