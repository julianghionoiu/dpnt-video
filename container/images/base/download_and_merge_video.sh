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

# Injected from lambda
ensure_env "PARTICIPANT_ID"
ensure_env "CHALLENGE_ID"
ensure_env "S3_URL_NEW_VIDEO"
ensure_env "S3_URL_ACCUMULATOR_VIDEO"
ensure_env "VIDEO_PUBLISH_DESTINATION_URL"

echo  "~~~~~~ Download and merge video into accumulator ~~~~~~" > /dev/null
VIDEOS_LIST="mylist.txt"

echo  "Downloading new screencast and the accumulator videos from the s3 bucket"
NEW_VIDEO_NAME=$(basename "${S3_URL_NEW_VIDEO}")
ACCUMULATOR_VIDEO_NAME=$(basename "${S3_URL_ACCUMULATOR_VIDEO}")
aws s3 cp "${S3_URL_NEW_VIDEO}" "${NEW_VIDEO_NAME}" --endpoint "${S3_ENDPOINT}"
aws s3 ls "${S3_URL_ACCUMULATOR_VIDEO}" --endpoint "${S3_ENDPOINT}" && \
          aws s3 cp "${S3_URL_ACCUMULATOR_VIDEO}" "${ACCUMULATOR_VIDEO_NAME}" --endpoint "${S3_ENDPOINT}" || true

if [[ -s "${ACCUMULATOR_VIDEO_NAME}" ]]; then

   echo "Scaling '${NEW_VIDEO_NAME}' to match accumulator, '${ACCUMULATOR_VIDEO_NAME}'"
   SCALE=$(ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=s=x:p=0 "${ACCUMULATOR_VIDEO_NAME}" | tr -d "[:space:]")
   echo "Accumultor scale is: ${SCALE}"
   SCALED_NEW_VIDEO_NAME="scaled_${NEW_VIDEO_NAME}"
   ffmpeg -i "${NEW_VIDEO_NAME}" -s "${SCALE}" -c:a copy "${SCALED_NEW_VIDEO_NAME}"

   echo  "Concatenating '${SCALED_NEW_VIDEO_NAME}' at the bottom of '${ACCUMULATOR_VIDEO_NAME}'"
   echo "file '${ACCUMULATOR_VIDEO_NAME}'" > ${VIDEOS_LIST}
   echo "file '${SCALED_NEW_VIDEO_NAME}'" >> ${VIDEOS_LIST}
   cat ${VIDEOS_LIST}
   ffmpeg -f concat -safe 0 -i ${VIDEOS_LIST} -c copy "new_${ACCUMULATOR_VIDEO_NAME}"

   echo "Replace accumulator with the merged video"
   rm "${ACCUMULATOR_VIDEO_NAME}"
   mv "new_${ACCUMULATOR_VIDEO_NAME}" "${ACCUMULATOR_VIDEO_NAME}"
else
   echo  "Copying '${NEW_VIDEO_NAME}' into '${ACCUMULATOR_VIDEO_NAME}' as accumulator video does not pre-exist"
   cp "${NEW_VIDEO_NAME}" "${ACCUMULATOR_VIDEO_NAME}"
fi

echo  "Uploading the merged video to '${S3_URL_ACCUMULATOR_VIDEO}'"
aws s3 cp "${ACCUMULATOR_VIDEO_NAME}" "${S3_URL_ACCUMULATOR_VIDEO}" --acl public-read --endpoint "${S3_ENDPOINT}"

echo  "~~~~~~ Publish results ~~~~~~" > /dev/null
if [[ "${SQS_QUEUE_URL}" != http* ]]; then
    echo "SQS_QUEUE_URL does not seem to be valid. Will print to the console and exit" > /dev/null
    echo "participant=${PARTICIPANT_ID} challengeId=${CHALLENGE_ID} videoLink=\"${VIDEO_PUBLISH_DESTINATION_URL}\""
    exit 0
fi

echo "Publish video to interop event queue" > /dev/null
INTEROP_QUEUE_CONFIG="${WORK_DIR}/sqs_queue.conf"
cat > "${INTEROP_QUEUE_CONFIG}" <<EOL
sqs {
  serviceEndpoint = "${SQS_ENDPOINT}"
  signingRegion = "${SQS_REGION}"
  queueUrl = "${SQS_QUEUE_URL}"
}
EOL
cat "${INTEROP_QUEUE_CONFIG}"
DRY_RUN=false java -Dconfig.file="${INTEROP_QUEUE_CONFIG}" \
    -jar "${WORK_DIR}/queue-cli-tool-all.jar" \
    send rawVideoUpdated \
    participant="${PARTICIPANT_ID}" challengeId="${CHALLENGE_ID}" videoLink="${VIDEO_PUBLISH_DESTINATION_URL}"
# Output: Public URL published to SQS or printed on the console
