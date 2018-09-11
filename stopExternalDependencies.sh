#!/bin/bash

set -e
set -u
set -o pipefail

stopLocalS3() {
    echo "~~~~~~~~~~ Stopping Local S3 service (stubbing AWS S3 service) ~~~~~~~~~"
    python local-s3/minio-wrapper.py stop || true
}

stopLocalSQS() {
    echo "~~~~~~~~~~ Stopping Local SQS service (stubbing AWS SQS service) ~~~~~~~~~"
    python local-sqs/elasticmq-wrapper.py stop || true
}

stopLocalECS() {
    echo "~~~~~~~~~~ Stopping Local ECS service (stubbing AWS ECS service) ~~~~~~~~~"
    python local-ecs/ecs-server-wrapper.py stop
    ecsPID=$(netstat -tulpn | grep :9988 | awk '{print $7}' | tr -d "/python")
    if [[ -z ${ecsPID[0]} ]]; then
        echo "Local ECS service has already been stopped"
    else
        echo "Killing Local ECS service"
        kill -9 ${ecsPID} && echo "Local ECS service has now been stopped"
    fi
}

stopLocalS3
stopLocalSQS
stopLocalECS
