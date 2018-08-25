package tdl.datapoint.video;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.TemporaryFolder;
import org.yaml.snakeyaml.Yaml;
import tdl.datapoint.video.support.LocalS3Bucket;
import tdl.datapoint.video.support.LocalSQSQueue;
import tdl.datapoint.video.support.S3Event;
import tdl.datapoint.video.support.SNSEvent;
import tdl.datapoint.video.support.TestVideoFile;
import tdl.participant.queue.connector.EventProcessingException;
import tdl.participant.queue.connector.QueueEventHandlers;
import tdl.participant.queue.connector.SqsEventQueue;
import tdl.participant.queue.events.RawVideoUpdatedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;


public class VideoDatapointAcceptanceTest {
    private static final Context NO_CONTEXT = null;
    private static final int WAIT_BEFORE_RETRY_IN_MILLIS = 2000;
    private static final int TASK_FINISH_CHECK_RETRY_COUNT = 10;

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private VideoUploadHandler videoUploadHandler;
    private SqsEventQueue sqsEventQueue;
    private LocalS3Bucket localS3Bucket;
    private List<RawVideoUpdatedEvent> rawVideoUpdatedEvents;
    private ObjectMapper mapper;

    @Before
    public void setUp() throws EventProcessingException, IOException {
        environmentVariables.set("AWS_ACCESS_KEY_ID","local_test_access_key");
        environmentVariables.set("AWS_SECRET_KEY","local_test_secret_key");
        setEnvFrom(environmentVariables, Paths.get("config", "local.params.yml"));

        localS3Bucket = LocalS3Bucket.createInstance(
                getEnv(ApplicationEnv.S3_ENDPOINT),
                getEnv(ApplicationEnv.S3_REGION));

        sqsEventQueue = LocalSQSQueue.createInstance(
                getEnv(ApplicationEnv.SQS_ENDPOINT),
                getEnv(ApplicationEnv.SQS_REGION),
                getEnv(ApplicationEnv.SQS_QUEUE_URL));

        videoUploadHandler = new VideoUploadHandler();

        QueueEventHandlers queueEventHandlers = new QueueEventHandlers();
        rawVideoUpdatedEvents = new ArrayList<>();
        queueEventHandlers.on(RawVideoUpdatedEvent.class, rawVideoUpdatedEvents::add);
        sqsEventQueue.subscribeToMessages(queueEventHandlers);

        mapper = new ObjectMapper();
    }

    private static String getEnv(ApplicationEnv key) {
        String env = System.getenv(key.name());
        if (env == null || env.trim().isEmpty() || "null".equals(env)) {
            throw new RuntimeException("[Startup] Environment variable " + key + " not set");
        }
        return env;
    }

    private static void setEnvFrom(EnvironmentVariables environmentVariables, Path path) throws IOException {
        String yamlString = Files.lines(path).collect(Collectors.joining("\n"));

        Yaml yaml = new Yaml();
        Map<String, String> values = yaml.load(yamlString);

        values.forEach(environmentVariables::set);
    }

    @After
    public void tearDown() throws Exception {
        sqsEventQueue.unsubscribeFromMessages();
    }

    @Test
    public void create_repo_and_uploads_commits() throws Exception {
        // Given - The participant produces SRCS files while solving a challenge
        String challengeId = "TCH";
        String participantId = generateId();
        String s3destination = String.format("%s/%s/file.srcs", challengeId, participantId);
        TestVideoFile srcsForTestChallenge = new TestVideoFile("HmmmLang_R1Cov33_R2Cov44.srcs");

        // When - Upload event happens
        S3Event s3Event = localS3Bucket.putObject(srcsForTestChallenge.asFile(), s3destination);
        videoUploadHandler.handleRequest(
                convertToMap(wrapAsSNSEvent(s3Event)),
                NO_CONTEXT);
        waitForQueueToReceiveEvents();

        // Then - Raw video uploaded events are computed for the deploy tags
        assertThat(rawVideoUpdatedEvents.size(), equalTo(2));
        System.out.println("Received video events: "+ rawVideoUpdatedEvents);
        rawVideoUpdatedEvents.sort(Comparator.comparing(RawVideoUpdatedEvent::getChallengeId));
        RawVideoUpdatedEvent rawVideoUploadedRound1 = rawVideoUpdatedEvents.get(0);
        assertThat(rawVideoUploadedRound1.getParticipant(), equalTo(participantId));
        assertThat(rawVideoUploadedRound1.getChallengeId(), equalTo(challengeId+"_R1"));
        assertThat(rawVideoUploadedRound1.getVideoLink(), equalTo(33));
        RawVideoUpdatedEvent rawVideoUploadedRound2 = rawVideoUpdatedEvents.get(1);
        assertThat(rawVideoUploadedRound2.getParticipant(), equalTo(participantId));
        assertThat(rawVideoUploadedRound2.getChallengeId(), equalTo(challengeId+"_R2"));
        assertThat(rawVideoUploadedRound2.getVideoLink(), equalTo(44));
    }

    private String wrapAsSNSEvent(S3Event s3Event) throws JsonProcessingException {
        SNSEvent snsEvent = new SNSEvent(mapper.writeValueAsString(s3Event.asJsonNode()));
        return mapper.writeValueAsString(snsEvent.asJsonNode());
    }

    //~~~~~~~~~~ Helpers ~~~~~~~~~~~~~`

    private void waitForQueueToReceiveEvents() throws InterruptedException {
        int retryCtr = 0;
        while ((rawVideoUpdatedEvents.size() < 2) && (retryCtr < TASK_FINISH_CHECK_RETRY_COUNT)) {
            Thread.sleep(WAIT_BEFORE_RETRY_IN_MILLIS);
            retryCtr++;
        }
    }

    private static String generateId() {
        return UUID.randomUUID().toString().replaceAll("-","");
    }

    private static Map<String, Object> convertToMap(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }
}
