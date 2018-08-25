#!/usr/bin/env bash

set -x
set -e
set -u
set -o pipefail

SCRIPT_CURRENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

function die() { echo >&2 $1; exit 1; }
[ "$#" -eq 1 ] || die "Usage: $0 STAGE"
STAGE=$1

STACK_NAME="dpnt-video-ecs-cluster-${STAGE}"
STACK_REGION="eu-west-1"
BUILD_DIR="${SCRIPT_CURRENT_DIR}/.build"

PARAMETERS_FILE="${SCRIPT_CURRENT_DIR}/../config/${STAGE}.params.yml"
TEMPLATE_FILE="${BUILD_DIR}/cloudformation-template-ecs-cluster-${STAGE}.json"

echo "Sanity check the template" > /dev/null
aws cloudformation validate-template \
    --template-body "file://${TEMPLATE_FILE}"

echo "Update stack" > /dev/null
aws cloudformation deploy \
    --stack-name ${STACK_NAME} \
    --region ${STACK_REGION} \
    --template-file "${TEMPLATE_FILE}" \
    --capabilities CAPABILITY_NAMED_IAM
