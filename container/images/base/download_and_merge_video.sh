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

ensure_env "S3_ENDPOINT"
ensure_env "S3_REGION"

ensure_env "SQS_ENDPOINT"
ensure_env "SQS_REGION"
ensure_env "SQS_QUEUE_URL"

ensure_env "PARTICIPANT_ID"
ensure_env "ROUND_ID"

echo  "~~~~~~ Download and merge video into accumulator ~~~~~~" > /dev/null
### --- do merge video and upload stuff here

echo  "~~~~~~ Publish results ~~~~~~" > /dev/null
merged_video_status="n video merged"

if [[ "${SQS_QUEUE_URL}" != http* ]]; then
    echo "SQS_QUEUE_URL does not seem to be valid. Will print to the console and exit" > /dev/null
    echo "participant=${PARTICIPANT_ID} roundId=${ROUND_ID}"
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
    send videoMerged \
    participant=${PARTICIPANT_ID} roundId=${ROUND_ID}
# Output: Public URL published to SQS or printed on the console
