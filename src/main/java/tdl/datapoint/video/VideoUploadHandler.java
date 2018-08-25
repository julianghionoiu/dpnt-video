package tdl.datapoint.video;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSAsyncClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import tdl.datapoint.video.processing.ECSVideoTaskRunner;
import tdl.datapoint.video.processing.LocalGitClient;
import tdl.datapoint.video.processing.S3BucketEvent;
import tdl.datapoint.video.processing.S3SrcsToGitExporter;
import tdl.participant.queue.connector.SqsEventQueue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static tdl.datapoint.video.ApplicationEnv.ECS_ENDPOINT;
import static tdl.datapoint.video.ApplicationEnv.ECS_REGION;
import static tdl.datapoint.video.ApplicationEnv.ECS_TASK_CLUSTER;
import static tdl.datapoint.video.ApplicationEnv.ECS_TASK_DEFINITION_PREFIX;
import static tdl.datapoint.video.ApplicationEnv.ECS_TASK_LAUNCH_TYPE;
import static tdl.datapoint.video.ApplicationEnv.ECS_VPC_ASSIGN_PUBLIC_IP;
import static tdl.datapoint.video.ApplicationEnv.ECS_VPC_SECURITY_GROUP;
import static tdl.datapoint.video.ApplicationEnv.ECS_VPC_SUBNET;
import static tdl.datapoint.video.ApplicationEnv.S3_ENDPOINT;
import static tdl.datapoint.video.ApplicationEnv.S3_REGION;
import static tdl.datapoint.video.ApplicationEnv.SQS_ENDPOINT;
import static tdl.datapoint.video.ApplicationEnv.SQS_QUEUE_URL;
import static tdl.datapoint.video.ApplicationEnv.SQS_REGION;

public class VideoUploadHandler implements RequestHandler<Map<String, Object>, String> {
    private static final Logger LOG = Logger.getLogger(VideoUploadHandler.class.getName());
    private AmazonS3 s3Client;
    private SqsEventQueue participantEventQueue;
    private S3SrcsToGitExporter srcsToGitExporter;
    private final ECSVideoTaskRunner ecsVideoTaskRunner;
    private ObjectMapper jsonObjectMapper;

    private static String getEnv(ApplicationEnv key) {
        String env = System.getenv(key.name());
        if (env == null || env.trim().isEmpty() || "null".equals(env)) {
            throw new RuntimeException("[Startup] Environment variable " + key + " not set");
        }
        return env;
    }

    @SuppressWarnings("WeakerAccess")
    public VideoUploadHandler() {
        s3Client = createS3Client(
                getEnv(S3_ENDPOINT),
                getEnv(S3_REGION));

        AmazonECS ecsClient = createECSClient(
                getEnv(ECS_ENDPOINT),
                getEnv(ECS_REGION));

        ecsVideoTaskRunner = new ECSVideoTaskRunner(ecsClient,
                getEnv(ECS_TASK_CLUSTER),
                getEnv(ECS_TASK_DEFINITION_PREFIX),
                getEnv(ECS_TASK_LAUNCH_TYPE),
                getEnv(ECS_VPC_SUBNET),
                getEnv(ECS_VPC_SECURITY_GROUP),
                getEnv(ECS_VPC_ASSIGN_PUBLIC_IP));

        srcsToGitExporter = new S3SrcsToGitExporter();

        AmazonSQS queueClient = createSQSClient(
                getEnv(SQS_ENDPOINT),
                getEnv(SQS_REGION)
        );
        String queueUrl = getEnv(SQS_QUEUE_URL);
        participantEventQueue = new SqsEventQueue(queueClient, queueUrl);

        jsonObjectMapper = new ObjectMapper();
    }

    private static AmazonS3 createS3Client(String endpoint, String region) {
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
        builder = builder.withPathStyleAccessEnabled(true)
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                .withCredentials(new DefaultAWSCredentialsProviderChain());
        return builder.build();
    }

    private static AmazonSQS createSQSClient(String serviceEndpoint, String signingRegion) {
        AwsClientBuilder.EndpointConfiguration endpointConfiguration =
                new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, signingRegion);
        return AmazonSQSClientBuilder.standard()
                .withEndpointConfiguration(endpointConfiguration)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();
    }

    private static AmazonECS createECSClient(String serviceEndpoint, String signingRegion) {
        AwsClientBuilder.EndpointConfiguration endpointConfiguration =
                new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, signingRegion);
        return AmazonECSAsyncClientBuilder.standard()
                .withEndpointConfiguration(endpointConfiguration)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();
    }

    @Override
    public String handleRequest(Map<String, Object> s3EventMap, Context context) {
        try {
            handleS3Event(S3BucketEvent.from(s3EventMap, jsonObjectMapper));
            return "OK";
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    private void handleS3Event(S3BucketEvent event) throws Exception {
        LOG.info("Process S3 event with: "+event);
        String participantId = event.getParticipantId();
        String challengeId = event.getChallengeId();

        LOG.info("Initialise local temp repo");
        Path tempDirectory = Files.createTempDirectory(participantId);
        Git localRepo = LocalGitClient.init(tempDirectory);
        LOG.info("Local repo initialised at "+localRepo.getRepository().getDirectory());

        LOG.info("Read repo from SRCS file "+event.getKey());
        S3Object remoteSRCSFile = s3Client.getObject(event.getBucket(), event.getKey());
        srcsToGitExporter.export(remoteSRCSFile, tempDirectory);
        LOG.info("SRCS file exported to: " + tempDirectory);

        LOG.info("Identify \"done\" tags");
        List<String> doneTags = LocalGitClient.getTags(localRepo).stream()
                .filter(s -> s.startsWith(challengeId))
                .filter(s -> s.endsWith("/done"))
                .collect(Collectors.toList());
        if (doneTags.isEmpty()) {
            LOG.info("No tags to process. Exiting");
            return;
        } else {
            LOG.info("Relevant tags "+doneTags);
        }

        LOG.info("Triggering ECS to process video for tags");
        for (String doneTag : doneTags) {
            String roundId = doneTag.split("/")[0];
            ecsVideoTaskRunner.runVideoTask(event.getBucket(), event.getKey(),
                    participantId, challengeId, roundId, doneTag);
        }
    }
}
