#!/bin/bash

set -e
set -u
set -o pipefail

startLocalS3() {
    echo "~~~~~~~~~~ Starting Local S3 service (stubbing AWS S3 service) ~~~~~~~~~"
    python3.7 local-s3/minio-wrapper.py start config/local.params.yml
}

startLocalSQS() {
    echo "~~~~~~~~~~ Starting Local SQS service (stubbing AWS SQS service) ~~~~~~~~~"
    python3.7 local-sqs/elasticmq-wrapper.py start
}

startLocalECS() {
    echo "~~~~~~~~~~ Starting Local ECS service (stubbing AWS ECS service) ~~~~~~~~~"
    OS_NAME=$(uname)
    if [[ ${OS_NAME} == "Linux" ]]; then
        DOCKER_HOST_WITHIN_CONTAINER=172.17.0.1 python3.7 local-ecs/ecs-server-wrapper.py start config/local.params.yml
    else
        python3.7 local-ecs/ecs-server-wrapper.py start config/local.params.yml
    fi
}

startLocalS3
startLocalSQS
startLocalECS
