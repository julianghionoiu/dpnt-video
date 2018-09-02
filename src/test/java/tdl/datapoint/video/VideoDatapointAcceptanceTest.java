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
import java.util.Arrays;
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
    private static final int FIRST_ACCUMULATED_VIDEO_EVENT = 0;

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private VideoUploadHandler videoUploadHandler;
    private SqsEventQueue sqsEventQueue;
    private LocalS3Bucket localS3AccumulatedVideoBucket;
    private LocalS3Bucket localS3SplitVideosBucket;
    private List<RawVideoUpdatedEvent> rawVideoUpdatedEvents;
    private ObjectMapper mapper;

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

    private static String generateId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    private static Map<String, Object> convertToMap(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    @Before
    public void setUp() throws EventProcessingException, IOException {
        environmentVariables.set("AWS_ACCESS_KEY_ID", "local_test_access_key");
        environmentVariables.set("AWS_SECRET_ACCESS_KEY", "local_test_secret_key");
        setEnvFrom(environmentVariables, Paths.get("config", "local.params.yml"));

        localS3SplitVideosBucket = LocalS3Bucket.createInstance(
                getEnv(ApplicationEnv.S3_ENDPOINT),
                getEnv(ApplicationEnv.S3_REGION),
                "tdl-official-split-videos");

        localS3AccumulatedVideoBucket = LocalS3Bucket.createInstance(
                getEnv(ApplicationEnv.S3_ENDPOINT),
                getEnv(ApplicationEnv.S3_REGION),
                "tdl-official-videos");

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

    @After
    public void tearDown() throws Exception {
        sqsEventQueue.unsubscribeFromMessages();
    }

    //~~~~~~~~~~ Helpers ~~~~~~~~~~~~~`

    @Test
    public void upload_first_video_when_accumulator_does_not_exist_yet() throws Exception {
        // Given - The participant produces Video files while solving a challenge
        String challengeId = "TCH";
        String participantId = generateId();
        String s3AccumulatorVideoDestination = String.format("%s/%s/real-recording.mp4", challengeId, participantId);
        TestVideoFile accumulatorVideo = new TestVideoFile("tdl/datapoint/video/first_video_upload/before/real-recording.mp4");
        localS3AccumulatedVideoBucket.putObject(accumulatorVideo.asFile(), s3AccumulatorVideoDestination);
        String s3destination = String.format("%s/%s/screencast_1.mp4", challengeId, participantId);
        TestVideoFile videoForTestChallenge = new TestVideoFile("screencast_20180727T144854.mp4");

        // When - Upload event happens
        S3Event s3Event = localS3SplitVideosBucket.putObject(videoForTestChallenge.asFile(), s3destination);
        videoUploadHandler.handleRequest(
                convertToMap(wrapAsSNSEvent(s3Event)),
                NO_CONTEXT);

        waitForQueueToReceiveEvents();

        // Then - Raw video uploaded events are computed for the deploy tags
        assertThat("Raw video update events match check: 1 events expected", rawVideoUpdatedEvents.size(), equalTo(1));
        System.out.println("Received video events: " + rawVideoUpdatedEvents);
        rawVideoUpdatedEvents.sort(Comparator.comparing(RawVideoUpdatedEvent::getChallengeId));
        RawVideoUpdatedEvent rawVideoUploaded = rawVideoUpdatedEvents.get(FIRST_ACCUMULATED_VIDEO_EVENT);
        assertThat("participantId matching", rawVideoUploaded.getParticipant(), equalTo(participantId));
        assertThat("challengeId matching", rawVideoUploaded.getChallengeId(), equalTo(challengeId));
        String key = String.format("%s/%s/real-recording.mp4", challengeId, participantId);
        //TODO check minio to see if we can get a http url & aws s3 - see docs
        assertThat("Video link match check: expecting something like s3://tdl-official-videos/.../real-recording.mp4", rawVideoUploaded.getVideoLink(), equalTo(
                String.format("s3://tdl-official-videos/%s", key))
        );

        Path expectedAccumulatorVideo = new TestVideoFile("tdl/datapoint/video/first_video_upload/after/real-recording.mp4").asFile().toPath();
        //TODO use Http instead of s3
        Path actualAccumulatorVideo = new TestVideoFile(localS3AccumulatedVideoBucket, key).getS3Object("real-recording", ".mp4").toPath();
        //TODO compare videos using dev-screen-record's logic: see acceptance test that generates and reads QRcode
        assertThatFilesAreEqual("Expect the files to match in content", actualAccumulatorVideo, expectedAccumulatorVideo);
    }

    @Test
    public void upload_when_accumulator_video_exists() throws Exception {
        // Given - The participant produces Video files while solving a challenge
        String challengeId = "TCH";
        String participantId = generateId();
        String s3AccumulatorVideoDestination = String.format("%s/%s/real-recording.mp4", challengeId, participantId);
        TestVideoFile accumulatorVideo = new TestVideoFile("tdl/datapoint/video/second_video_upload/before/real-recording.mp4");
        localS3AccumulatedVideoBucket.putObject(accumulatorVideo.asFile(), s3AccumulatorVideoDestination);
        String s3SecondVideoDestination = String.format("%s/%s/screencast_2.mp4", challengeId, participantId);
        TestVideoFile s3SecondVideo = new TestVideoFile("screencast_20180727T225445.mp4");

        // When - Upload event happens
        S3Event s3Event = localS3SplitVideosBucket.putObject(s3SecondVideo.asFile(), s3SecondVideoDestination);
        videoUploadHandler.handleRequest(
                convertToMap(wrapAsSNSEvent(s3Event)),
                NO_CONTEXT);

        waitForQueueToReceiveEvents();

        // Then - Raw video uploaded events are computed for the deploy tags
        assertThat("Raw video update events match check: 1 events expected", rawVideoUpdatedEvents.size(), equalTo(1));
        System.out.println("Received video events: " + rawVideoUpdatedEvents);
        rawVideoUpdatedEvents.sort(Comparator.comparing(RawVideoUpdatedEvent::getChallengeId));
        RawVideoUpdatedEvent rawVideoUploaded = rawVideoUpdatedEvents.get(FIRST_ACCUMULATED_VIDEO_EVENT);
        assertThat("participantId matching", rawVideoUploaded.getParticipant(), equalTo(participantId));
        assertThat("challengeId matching", rawVideoUploaded.getChallengeId(), equalTo(challengeId));
        String key = String.format("%s/%s/real-recording.mp4", challengeId, participantId);
        //TODO check minio to see if we can get a http url & aws s3 - see docs
        assertThat("Video link match check: expecting something like s3://tdl-official-videos/.../real-recording.mp4", rawVideoUploaded.getVideoLink(), equalTo(
                String.format("s3://tdl-official-videos/%s", key))
        );

        Path expectedAccumulatorVideo = new TestVideoFile("tdl/datapoint/video/second_video_upload/after/real-recording.mp4").asFile().toPath();
        //TODO use Http instead of s3
        Path actualAccumulatorVideo = new TestVideoFile(localS3AccumulatedVideoBucket, key).getS3Object("real-recording", ".mp4").toPath();
        //TODO compare videos using dev-screen-record's logic: see acceptance test that generates and reads QRcode
        assertThatFilesAreEqual("Expect the files to match in content", actualAccumulatorVideo, expectedAccumulatorVideo);
    }

    private void assertThatFilesAreEqual(String reason, Path firstFile, Path secondFile) throws IOException {
        byte[] firstFileAsBytes = Files.readAllBytes(firstFile);
        byte[] secondFileAsBytes = Files.readAllBytes(secondFile);

        assertThat(reason,
                Arrays.equals(firstFileAsBytes, secondFileAsBytes), equalTo(true));
    }

    private String wrapAsSNSEvent(S3Event s3Event) throws JsonProcessingException {
        SNSEvent snsEvent = new SNSEvent(mapper.writeValueAsString(s3Event.asJsonNode()));
        return mapper.writeValueAsString(snsEvent.asJsonNode());
    }

    private void waitForQueueToReceiveEvents() throws InterruptedException {
        int retryCtr = 0;
        while ((rawVideoUpdatedEvents.size() < 3) && (retryCtr < TASK_FINISH_CHECK_RETRY_COUNT)) {
            Thread.sleep(WAIT_BEFORE_RETRY_IN_MILLIS);
            retryCtr++;
        }
    }
}
