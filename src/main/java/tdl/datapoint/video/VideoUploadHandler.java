package tdl.datapoint.video;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSAsyncClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import tdl.datapoint.video.processing.ECSVideoTaskRunner;
import tdl.datapoint.video.processing.S3BucketEvent;
import tdl.datapoint.video.security.TokenEncryptionService;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static tdl.datapoint.video.ApplicationEnv.ECS_ENDPOINT;
import static tdl.datapoint.video.ApplicationEnv.ECS_REGION;
import static tdl.datapoint.video.ApplicationEnv.ECS_TASK_CLUSTER;
import static tdl.datapoint.video.ApplicationEnv.ECS_TASK_DEFINITION_PREFIX;
import static tdl.datapoint.video.ApplicationEnv.ECS_TASK_LAUNCH_TYPE;
import static tdl.datapoint.video.ApplicationEnv.ECS_VPC_ASSIGN_PUBLIC_IP;
import static tdl.datapoint.video.ApplicationEnv.ECS_VPC_SECURITY_GROUP;
import static tdl.datapoint.video.ApplicationEnv.ECS_VPC_SUBNET;

class VideoUploadHandler implements RequestHandler<Map<String, Object>, String> {
    private static final Logger LOG = Logger.getLogger(VideoUploadHandler.class.getName());
    private final ECSVideoTaskRunner ecsVideoTaskRunner;
    private final TokenEncryptionService tokenEncryptionService;
    private final ObjectMapper jsonObjectMapper;
    private final String accumulatorVideoName;
    private final String accumulatedVideosBucketName;

    @SuppressWarnings("WeakerAccess")
    public VideoUploadHandler(String accumulatorVideoName,
                              String accumulatedVideosBucketName,
                              String secret) {
        this.accumulatorVideoName = accumulatorVideoName;
        this.accumulatedVideosBucketName = accumulatedVideosBucketName;

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

        jsonObjectMapper = new ObjectMapper();

        try {
            tokenEncryptionService = new TokenEncryptionService(secret);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            LOG.log(Level.SEVERE, "Could not create hash due to error: " + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    private static String getEnv(ApplicationEnv key) {
        String env = System.getenv(key.name());
        if (env == null || env.trim().isEmpty() || "null".equals(env)) {
            throw new RuntimeException("[Startup] Environment variable " + key + " not set");
        }
        return env;
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
            handleS3Event(S3BucketEvent.from(s3EventMap, jsonObjectMapper),
                    accumulatorVideoName,
                    accumulatedVideosBucketName
            );
            return "OK";
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    private void handleS3Event(S3BucketEvent event,
                               String accumulatorVideoName,
                               String accumulatedVideosBucketName) throws UnsupportedEncodingException {
        LOG.info("Process S3 event with: " + event);

        String participantId = event.getParticipantId();
        String challengeId = event.getChallengeId();

        String splitVideosBucketName = event.getBucket();
        final String s3UrlNewVideo = String.format("s3://%s/%s", splitVideosBucketName, event.getKey());

        final String hash = tokenEncryptionService.createHashFrom(challengeId, participantId);
        final String s3BucketKey = String.format("%s/%s/%s/%s", challengeId, participantId, hash, accumulatorVideoName);
        final String s3UrlAccumulatorVideo = String.format("s3://%s/%s", accumulatedVideosBucketName, s3BucketKey);

        LOG.info("Triggering ECS to process video for tags");
        ecsVideoTaskRunner.runVideoTask(
                participantId, challengeId, s3UrlNewVideo, s3UrlAccumulatorVideo, accumulatorVideoName
        );
    }
}
