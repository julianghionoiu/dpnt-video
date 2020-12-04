#!/bin/bash

set -e
set -u
set -o pipefail

startLocalECS() {
    echo "~~~~~~~~~~ Starting Local ECS service (stubbing AWS ECS service) ~~~~~~~~~"
    OS_NAME=$(uname)
    if [[ ${OS_NAME} == "Linux" ]]; then
        if [[ -e /.dockerenv ]]; then
            DOCKER_HOST_WITHIN_CONTAINER=localhost python3.7 local-ecs/ecs-server-wrapper.py start config/local.params.yml
        else
            DOCKER_HOST_WITHIN_CONTAINER=172.17.0.1 python3.7 local-ecs/ecs-server-wrapper.py start config/local.params.yml
        fi
    else
        DOCKER_HOST_WITHIN_CONTAINER=docker.for.mac.host.internal python3.7 local-ecs/ecs-server-wrapper.py start config/local.params.yml
    fi
}

startLocalSQS() {
    echo "~~~~~~~~~~ Starting Local SQS service (stubbing AWS SQS service) ~~~~~~~~~"
    python3.7 local-sqs/elasticmq-wrapper.py start
}

startLocalS3() {
    echo "~~~~~~~~~~ Starting Local S3 service (stubbing AWS S3 service) ~~~~~~~~~"
    python3.7 local-s3/minio-wrapper.py start config/local.params.yml
    ./local-s3/.cache/mc policy --recursive public s3/tdl-test-video-accumulator || true
}

startLocalECS
startLocalSQS
startLocalS3


