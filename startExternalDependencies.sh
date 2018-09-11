#!/bin/bash

set -e
set -u
set -o pipefail

startLocalS3() {
    echo "~~~~~~~~~~ Starting Local S3 service (stubbing AWS S3 service) ~~~~~~~~~"
    python local-s3/minio-wrapper.py start
    MINIO_CMD="minio"
    VERIFY_MINIO_CMD=$(which ${MINIO_CMD} || true)
    if [[ ${VERIFY_MINIO_CMD} == "${MINIO_CMD} not found" ]]; then
        MINIO_CMD="mc"
    fi
    ${MINIO_CMD} config host add myminio http://192.168.1.190:9000 local_test_access_key local_test_secret_key
    ${MINIO_CMD} policy --recursive public myminio/tdl-official-videos
}

startLocalSQS() {
    echo "~~~~~~~~~~ Starting Local SQS service (stubbing AWS SQS service) ~~~~~~~~~"
    python local-sqs/elasticmq-wrapper.py start
}

startLocalECSInConsoleMode() {
    echo "~~~~~~~~~~ Starting Local ECS service (stubbing AWS ECS service) ~~~~~~~~~"
    OS_NAME=$(uname)
    if [[ ${OS_NAME} == "Linux" ]]; then
        DOCKER_HOST_WITHIN_CONTAINER=172.17.0.1 python local-ecs/ecs-server-wrapper.py console config/local.params.yml
    else
        python local-ecs/ecs-server-wrapper.py console config/local.params.yml
    fi
}

startLocalS3
startLocalSQS
startLocalECSInConsoleMode
