package tdl.datapoint.video;

import tdl.datapoint.video.barcode.OutputToBarcodeMatrixReader;
import tdl.datapoint.video.support.TestVideoFile;
import tdl.datapoint.video.time.FakeTimeSource;
import tdl.datapoint.video.video.VideoPlayer;
import tdl.datapoint.video.video.VideoPlayerException;
import tdl.participant.queue.events.RawVideoUpdatedEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class CompareVideos {
    private static final int ACCUMULATED_VIDEO_EVENT = 0;
    private final String challengeId;
    private final String participantId;

    CompareVideos(String challengeId,
                  String participantId) {
        this.challengeId = challengeId;
        this.participantId = participantId;
    }

    void assertThatTheVideosMatchAfterMerging(List<RawVideoUpdatedEvent> rawVideoUpdatedEvents, String accumulatedVideoFile)
            throws IOException, VideoPlayerException, InterruptedException {
        assertThat("Raw video update events match check: 1 events expected", rawVideoUpdatedEvents.size(), equalTo(1));
        System.out.println("Received video events: " + rawVideoUpdatedEvents);
        rawVideoUpdatedEvents.sort(Comparator.comparing(RawVideoUpdatedEvent::getChallengeId));
        RawVideoUpdatedEvent rawVideoUploaded = rawVideoUpdatedEvents.get(ACCUMULATED_VIDEO_EVENT);
        assertThat("participantId matching", rawVideoUploaded.getParticipant(), equalTo(participantId));
        assertThat("challengeId matching", rawVideoUploaded.getChallengeId(), equalTo(challengeId));

        Path expectedAccumulatorVideo = new TestVideoFile(accumulatedVideoFile).asFile().toPath();
        Path actualAccumulatorVideo = new TestVideoFile(rawVideoUploaded.getVideoLink()).downloadFile();
        assertThatVideoFilesAreTheSame(actualAccumulatorVideo, expectedAccumulatorVideo);
    }

    private void assertThatVideoFilesAreTheSame(Path firstFile, Path secondFile)
            throws InterruptedException, IOException, VideoPlayerException {
        final List<OutputToBarcodeMatrixReader.TimestampedPayload>
                firstVideoFileBarCodes = getBarcodeFromVideo(firstFile.toString());

        final List<OutputToBarcodeMatrixReader.TimestampedPayload>
                secondVideoFileBarCodes = getBarcodeFromVideo(secondFile.toString());

        assertThat("Checking if the two videos have the same number of frames",
                firstVideoFileBarCodes.size(), equalTo(secondVideoFileBarCodes.size()));

        for (int frameIndex = 0; frameIndex < firstVideoFileBarCodes.size(); frameIndex++) {
            assertDecodedBarcode(firstVideoFileBarCodes.get(frameIndex), secondVideoFileBarCodes.get(frameIndex));
        }
    }

    private void assertDecodedBarcode(OutputToBarcodeMatrixReader.TimestampedPayload firstVideoFrame,
                                      OutputToBarcodeMatrixReader.TimestampedPayload secondVideoFrame) {
        Long timestamp = firstVideoFrame.videoTimestamp;

        assertThat("[" + timestamp + "] videoTimestamp", firstVideoFrame.videoTimestamp, is(secondVideoFrame.videoTimestamp));
        assertThat("[" + timestamp + "] topLeftPayload", firstVideoFrame.topLeftPayload, is(secondVideoFrame.topLeftPayload));
        assertThat("[" + timestamp + "] topRightPayload", firstVideoFrame.topRightPayload, is(secondVideoFrame.topRightPayload));
        assertThat("[" + timestamp + "] bottomLeftPayload", firstVideoFrame.bottomLeftPayload, is(secondVideoFrame.bottomLeftPayload));
        assertThat("[" + timestamp + "] bottomRightPayload", firstVideoFrame.bottomRightPayload, is(secondVideoFrame.bottomRightPayload));
    }
    
    private List<OutputToBarcodeMatrixReader.TimestampedPayload> getBarcodeFromVideo(String path)
            throws VideoPlayerException, InterruptedException, IOException {
        OutputToBarcodeMatrixReader barcodeReader = new OutputToBarcodeMatrixReader(new FakeTimeSource());
        VideoPlayer videoPlayer = new VideoPlayer(barcodeReader, new FakeTimeSource());
        videoPlayer.open(path);

        videoPlayer.play();
        
        videoPlayer.close();
        return barcodeReader.getDecodedBarcodes().stream()
                .filter(this::isPayloadConsistent)
                .collect(Collectors.toList());
    }

    private boolean isPayloadConsistent(OutputToBarcodeMatrixReader.TimestampedPayload payload) {
        int timebaseIncrement = 200;
        long expectedPayload = (payload.videoTimestamp - 1) * timebaseIncrement;
        try {
            return (Long.parseLong(payload.topLeftPayload) == expectedPayload)
                    && (Long.parseLong(payload.topRightPayload) == expectedPayload)
                    && (Long.parseLong(payload.bottomLeftPayload) == expectedPayload)
                    && (Long.parseLong(payload.bottomRightPayload) == expectedPayload);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
