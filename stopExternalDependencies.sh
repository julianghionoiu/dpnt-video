#!/bin/bash

set -e
set -u
set -o pipefail

stopLocalS3() {
    echo "~~~~~~~~~~ Stopping Local S3 service (stubbing AWS S3 service) ~~~~~~~~~"
    python3.7 local-s3/minio-wrapper.py stop
}

stopLocalSQS() {
    echo "~~~~~~~~~~ Stopping Local SQS service (stubbing AWS SQS service) ~~~~~~~~~"
    python3.7 local-sqs/elasticmq-wrapper.py stop
}

stopLocalECS() {
    echo "~~~~~~~~~~ Stopping Local ECS service (stubbing AWS ECS service) ~~~~~~~~~"
    python3.7 local-ecs/ecs-server-wrapper.py stop
}

stopLocalS3
stopLocalSQS
stopLocalECS
