# dpnt-video

Merge videos uploaded by user

- [Hmmm](./container/images/hmmm/)

### Updating sub-modules

Root project contains three git submodules:

- local-sqs
- local-s3
- local-ecs

Run the below command in the project root to update the above submodules:

```bash
git submodule update --init
```

## Acceptance test

Start the local S3 and SQS simulators
```bash
python local-sqs/elasticmq-wrapper.py start
python local-s3/minio-wrapper.py start
minio config host add myminio http://192.168.1.190:9000 local_test_access_key local_test_secret_key
```

We use the `hmmm` language to test the application.
The language container image needs to be build and tagged as `latest`:
```bash
./container/buildDockerImage.sh hmmm
```

**Note: to avoid failing VideoUploadAcceptanceTest acceptance tests due to timeouts please follow the below instructions** 

Start the local ECS simulator. The simulator will use the containers available in the local Docker registry.

Try the below first:

```bash 
ping host.docker.internal
```

If there is response, run the below command:

```bash
python local-ecs/ecs-server-wrapper.py start config/local.params.yml
```

Otherwise see below:

A note on the container networking. The container will attempt to call services on the docker host by using the `host.docker.internal` name.
https://docs.docker.com/docker-for-mac/networking/#use-cases-and-workarounds. 

Also see https://stackoverflow.com/questions/48546124/what-is-linux-equivalent-of-docker-for-mac-host-internal (especially for Windows or MacOS)
If this is not supported on your machine, you have the option of changing the hostname used to locate the Docker host:
```bash
DOCKER_HOST_WITHIN_CONTAINER=host.docker.internal python local-ecs/ecs-server-wrapper.py start config/local.params.yml
```
or

```bash
DOCKER_HOST_WITHIN_CONTAINER=n.n.n.n python local-ecs/ecs-server-wrapper.py start config/local.params.yml
```

#### Linux
`host.docker.internal` or `n.n.n.n` static ip address (for e.g. 172.17.0.1) on which the containers are running  

#### MacOS
`docker.for.mac.host.internal` or `docker.for.mac.localhost` or `n.n.n.n` - supported DNS entry of host (via Docker Host for MacOS) on which the containers are running 

#### Windows
`docker.for.win.host.internal` or `docker.for.win.localhost` or `n.n.n.n` - supported DNS entry of host (via Docker Host for Windows) on which the containers are running

### Run the acceptance test

```bash
./gradlew --rerun-tasks test
```

Stop dependencies
```bash
python local-sqs/elasticmq-wrapper.py stop
python local-ecs/ecs-server-wrapper.py stop
python local-s3/minio-wrapper.py stop
```

## Packaging

Install Serverless

Ensure you have new version (v6.4.0) of `npm` installed, installing `serverless` fails with older versions of npm:

```bash
npm install -g npm         # optional: to get the latest version of npm
npm install -g serverless

serverless info
```

## Local testing

Build package
```bash
./gradlew clean test shadowJar
```

Setup local bucket

```bash
export AWS_PROFILE=befaster                      # pre-configured profile contained in ~/.aws/credentials

minio mb myminio
minio mb myminio/tdl-test-auth/TCH/user01/
minio cp ./build/resources/test/video1.mpg myminio/tdl-test-auth/TCH/user01/video1.mpg
```

Invoke function manually

```bash
SLS_DEBUG=* serverless invoke local --function call-ecs-to-merge-videos --path src/test/resources/tdl/datapoint/merge-videos/sample_s3_via_sns_event.json
```

Note: the `sample_s3_via_sns_event.json` file contains the reference to the bucket `tdl-test-auth` and the key referring to the file at `TCH/user01/video1.mpg`.

## Container deployment

See the AWS ECR registry instructions on how to deploy a container into AWS


## Cluster deployment

Define an environment by duplicating the configuration file in `./config`

Trigger AWS CloudFormation to deploy or update an ECS Cluster
```bash
./ecs-cluster-definition/deploy.sh dev
```

## Lambda deployment

Build package
```bash
./gradlew clean test shadowJar
```

Create config file for respective env profiles:

```bash
cp config/local.params.yml config/dev.params.yml
```

or

```bash
cp config/dev.params.yml config/live.params.yml
```

Setup environment variables

```bash
export AWS_PROFILE=befaster                        # pre-configured profile contained in ~/.aws/credentials
```

Deploy to DEV
```bash
serverless deploy --stage dev
```

Deploy to LIVE
```bash
serverless deploy --stage live
```

## Remote testing

Create an S3 event json and place it in a temp folder, say `xyz/s3_event.json`
Set the bucket and the key to some meaningful values.

Invoke the dev lambda
```bash
SLS_DEBUG=* serverless invoke --stage dev --function call-ecs-to-merge-videos --path src/test/resources/tdl/datapoint/video/sample_s3_via_sns_event.json
```

Check the destination queue for that particular environment.
Check the ECS Task status and logs

Note: the `sample_s3_via_sns_event.json` file contains the reference to the bucket `tdl-test-auth` and the key referring to the file at `TCH/user01/video1.mpg`.
