package tdl.datapoint.video;

import tdl.datapoint.video.barcode.OutputToBarcodeMatrixReader;
import tdl.datapoint.video.support.RemoteVideoFile;
import tdl.datapoint.video.support.TestVideoFile;
import tdl.datapoint.video.time.FakeTimeSource;
import tdl.datapoint.video.video.VideoPlayer;
import tdl.datapoint.video.video.VideoPlayerException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class CompareVideos {

    void assertThatVideoFilesAreTheSame(TestVideoFile resourceVideo, RemoteVideoFile remoteVideo)
            throws InterruptedException, IOException, VideoPlayerException {
        Path expectedVideo = resourceVideo.asFile().toPath();
        Path actualVideo;
        try {
            actualVideo = remoteVideo.download();
        } catch (IOException e) {
            throw new AssertionError("Could not download "+remoteVideo, e);
        }

        final List<OutputToBarcodeMatrixReader.TimestampedPayload>
                firstVideoFileBarCodes = getBarcodeFromVideo(expectedVideo.toAbsolutePath().toString());

        final List<OutputToBarcodeMatrixReader.TimestampedPayload>
                secondVideoFileBarCodes = getBarcodeFromVideo(actualVideo.toAbsolutePath().toString());

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
