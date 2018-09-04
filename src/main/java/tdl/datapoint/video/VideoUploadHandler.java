package tdl.datapoint.video;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSAsyncClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import tdl.datapoint.video.processing.ECSVideoTaskRunner;
import tdl.datapoint.video.processing.S3BucketEvent;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
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
import static tdl.datapoint.video.ApplicationEnv.S3_ENDPOINT;
import static tdl.datapoint.video.ApplicationEnv.S3_REGION;

public class VideoUploadHandler implements RequestHandler<Map<String, Object>, String> {
    private static final Logger LOG = Logger.getLogger(VideoUploadHandler.class.getName());
    private final ECSVideoTaskRunner ecsVideoTaskRunner;
    private AmazonS3 s3Client;
    private ObjectMapper jsonObjectMapper;
    private String accumulatorVideoName;
    private String splitVideosBucketName;
    private String accumulatedVideosBucketName;
    private String secret;

    @SuppressWarnings("WeakerAccess")
    public VideoUploadHandler(String accumulatorVideoName,
                              String splitVideosBucketName,
                              String accumulatedVideosBucketName,
                              String secret) {
        this.accumulatorVideoName = accumulatorVideoName;
        this.splitVideosBucketName = splitVideosBucketName;
        this.accumulatedVideosBucketName = accumulatedVideosBucketName;
        this.secret = secret;

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

        jsonObjectMapper = new ObjectMapper();
    }

    private static String getEnv(ApplicationEnv key) {
        String env = System.getenv(key.name());
        if (env == null || env.trim().isEmpty() || "null".equals(env)) {
            throw new RuntimeException("[Startup] Environment variable " + key + " not set");
        }
        return env;
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
            handleS3Event(S3BucketEvent.from(s3EventMap, jsonObjectMapper),
                    accumulatorVideoName,
                    splitVideosBucketName,
                    accumulatedVideosBucketName,
                    secret);
            return "OK";
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    private void handleS3Event(S3BucketEvent event,
                               String accumulatorVideoName,
                               String splitVideosBucketName,
                               String accumulatedVideosBucketName,
                               String secret) {
        LOG.info("Process S3 event with: " + event);

        String participantId = event.getParticipantId();
        String challengeId = event.getChallengeId();

        final String s3UrlNewVideo = String.format("s3://%s/%s", splitVideosBucketName, event.getKey());

        final String hash = createHashFrom(challengeId, participantId, secret);
        final String s3BucketKey = String.format("%s/%s/%s/%s", challengeId, participantId, hash, accumulatorVideoName);
        final String s3UrlAccumulatorVideo = String.format("s3://%s/%s", accumulatedVideosBucketName, s3BucketKey);

        LOG.info("Triggering ECS to process video for tags");
        ecsVideoTaskRunner.runVideoTask(
                participantId, challengeId, s3UrlNewVideo, s3UrlAccumulatorVideo, accumulatorVideoName
        );
    }

    public String createHashFrom(String challengeId, String participantId, String secret) {
        try {
            return makeSHA1Hash(challengeId + participantId + secret);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            LOG.log(Level.SEVERE, "Could not create hash due to error: " + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    private String makeSHA1Hash(String input)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA1");
        md.reset();
        byte[] buffer = input.getBytes("UTF-8");
        md.update(buffer);
        byte[] digest = md.digest();

        StringBuilder hexStr = new StringBuilder();
        for (byte aDigest : digest) {
            hexStr.append(Integer.toString((aDigest & 0xff) + 0x100, 16).substring(1));
        }
        return hexStr.toString();
    }
}
