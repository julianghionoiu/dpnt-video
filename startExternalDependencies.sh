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
        python3.7 local-ecs/ecs-server-wrapper.py start config/local.params.yml
    fi
}

startLocalSQS() {
    echo "~~~~~~~~~~ Starting Local SQS service (stubbing AWS SQS service) ~~~~~~~~~"
    python3.7 local-sqs/elasticmq-wrapper.py start
}

startLocalS3() {
    echo "~~~~~~~~~~ Starting Local S3 service (stubbing AWS S3 service) ~~~~~~~~~"
    python3.7 local-s3/minio-wrapper.py start config/local.params.yml
    sleep 5
    ./local-s3/.cache/mc config host add one27001 http://127.0.0.1:9000 local_test_access_key local_test_secret_key || true
    ./local-s3/.cache/mc config host add one721702 http://172.17.0.2:9000 local_test_access_key local_test_secret_key || true
    ./local-s3/.cache/mc config host add localhost http://localhost:9000 local_test_access_key local_test_secret_key || true
    ./local-s3/.cache/mc policy --recursive public s3/tdl-test-video-accumulator || true
}

startLocalECS
startLocalSQS
startLocalS3

if [ -e /.dockerenv ]; then
    while true; do
        echo -n .
        sleep 5
    done    
fi

