
# The video upload container

Use the scripts provided to create the base container.

The build follows a single-stage process:
1. Build the base image `./base`

In order to run this in one go, you can use:
```
./buildDockerImage.sh
```

## Versioning

Each folder, including `base` contains a `version.txt` file.
This file should be incremented when the version changes.

The `latest` tag has a special meaning and it is used by the ECS local simulator to match the Docker container to be run.

## Running

In a nutshell the container operate in 3 steps:
1. Download the video
2. Download the accumulator
3. Merge video into accumulator video
4. Upload accumulator video
5. Publish event to SQS queue 

When working with S3 files, appropriate AWS S3 ENV variables should be provided.
Step `5.` can publish to console (`echo`) or to an SQS queue. If AWS SQS ENV variables are populated, SQS will be used.


## Manual Testing

The containers can be tested against public Git repos.
Example:
```
./runDockerContainer.sh participant round

# should display "xxxxx"
```
Running in this way will cover everything except reading from S3 and publishing to SQS.


## Automated Testing

By running the `local-ecs` and then the Acceptance test, one will cover the:
- passing AWS ENV variables into container
- reading video(s)from S3
- merge video(s) to accumulator video
- uploading accumulator video to S3 bucket 
- publishing public URL to SQS

## Debugging container

To be able to interactively log into the container and debug the state or even run further commands manually we have the follow command:

```
    DEBUG=true ./runDockerContainer.sh [rest of the args]

For e.g.
    DEBUG=true ./runDockerContainer.sh participant round
```

Inside the container under `/debug-repo/` you can place any repo or file or folders - volume sharing between host and container environments.

In case a repo is placed there, and would like to pass it to the `download_and_merge_video.sh` command:
```
./download_and_merge_video.sh [xxxxx] 

xxxxx = participant round                                                    
``` 

In case the folder does not have a `.git` folder, create one by running `git init` inside that folder. 
