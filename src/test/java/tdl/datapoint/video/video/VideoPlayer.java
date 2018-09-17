package tdl.datapoint.video.video;

import io.humble.video.*;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;
import tdl.datapoint.video.VideoUploadHandler;
import tdl.datapoint.video.output.ImageOutput;
import tdl.datapoint.video.output.ImageOutputException;
import tdl.datapoint.video.time.TimeSource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VideoPlayer {
    private static final Logger LOG = Logger.getLogger(VideoUploadHandler.class.getName());

    private final ImageOutput imageOutput;
    private final TimeSource timeSource;
    private Demuxer demuxer;
    private DemuxerStream videoStream;
    private MediaPicture picture;
    private MediaPictureConverter converter;
    private Rational streamTimebase;
    private Rational systemTimeBase;
    private long previousStreamEndTime;
    private LocalLoggerAdapter log;

    public VideoPlayer(ImageOutput imageOutput, TimeSource timeSource) {
        this.imageOutput = imageOutput;
        this.timeSource = timeSource;
        this.log = new LocalLoggerAdapter();
    }

    public void open(String filename) throws VideoPlayerException {
        try {
            imageOutput.open();
        } catch (ImageOutputException e) {
            throw new VideoPlayerException("Failed to open video source",e);
        }

        // A demuxer can separate the media streams from a media file ( video, audio, subtitles )
        demuxer = Demuxer.make();
        try {
            demuxer.open(filename, null, false, true, null, null);
        } catch (InterruptedException | IOException e) {
            throw new VideoPlayerException("Failed to open demuxer", e);
        }


        try {
            videoStream = getFirstVideoStreamFrom(demuxer)
                    .orElseThrow(() -> new RuntimeException("Could not find video stream in container " + filename));
        } catch (InterruptedException | IOException e) {
            throw new VideoPlayerException("Failed to retrieve video stream from file", e);
        }


        int videoStreamId = videoStream.getIndex();
        log.debug("videoStreamId {}",videoStreamId);
        log.debug("stream.getIndex() {}",videoStream.getIndex());
        Decoder videoDecoder = videoStream.getDecoder();

        /*
         * Now we have found the video stream in this file.  Let's open up our decoder so it can
         * do work.
         */
        videoDecoder.open(null, null);

        /*
          Care must be taken so that the picture is decoded and converted into a meaningful format for the output
         */
        picture = MediaPicture.make(
                videoDecoder.getWidth(),
                videoDecoder.getHeight(),
                videoDecoder.getPixelFormat());
        BufferedImage outputSample = imageOutput
                .getSuggestedOutputSample(videoDecoder.getWidth(), videoDecoder.getHeight());
        converter = MediaPictureConverterFactory.createConverter(
                outputSample,
                picture);


        /*
           With videos it is all about timing.
           We need to make sure we represent the two time frames:
            - The input reference (video)
            - The output reference, if you are rendering to the screen, that is the real time

           If you play with the references, it is possible to speed up or slow down the passing of time
         */
        streamTimebase = videoStream.getTimeBase();
        log.debug("streamTimebase  {}",streamTimebase);
        long streamVideoDuration = videoStream.getDuration();
        log.debug("streamVideoDuration {}",streamVideoDuration);

        // Set units for the system time, which will be in nanoseconds.
        systemTimeBase = Rational.make(1, 1000000000);
        log.debug("systemTimeBase {}",systemTimeBase);
        long systemVideoDuration = systemTimeBase.rescale(streamVideoDuration, streamTimebase);
        log.debug("systemVideoDuration {}",systemVideoDuration);

        // Reset time counters
        previousStreamEndTime = -1;
    }

    public Duration getDuration() {
        Rational systemTimeBase = Rational.make(1);
        long durationInSec = systemTimeBase
                .rescale(videoStream.getDuration(), videoStream.getTimeBase());
        return Duration.of(durationInSec, ChronoUnit.SECONDS);
    }

    public void play() throws InterruptedException, IOException {

        BufferedImage image = null;

        // Calculate the time BEFORE we start playing.
        long streamStartTime;
        if (previousStreamEndTime > -1) {
            streamStartTime = previousStreamEndTime;
        } else {
            streamStartTime = videoStream.getStartTime();
        }

        long systemStartTime = timeSource.currentTimeNano();


        /*
          One important thing to bare in mind is that the objects are being reused for performance reasons.
          This packet and the picture will be reset whenever we have new data.
         */
        final MediaPacket packet = MediaPacket.make();
        while (demuxer.read(packet) >= 0) {
            // Check if the packet belongs to the video stream
            if (packet.getStreamIndex() == videoStream.getIndex()) {
                int offset = 0;
                int bytesRead = 0;

                // Consume all the frames in the current packet
                do {
                    bytesRead += videoStream.getDecoder().decode(picture, packet, offset);
                    if (picture.isComplete()) {
                        image = displayVideoAtCorrectTime(streamStartTime, picture,
                                converter, image, imageOutput, systemStartTime, systemTimeBase,
                                streamTimebase);
                        previousStreamEndTime = picture.getTimeStamp();
                    }
                    offset += bytesRead;
                } while (offset < packet.getSize());
            }
        }

        /*
          Flush the encoder by reading data until we get a new (incomplete) picture
         */
        do {
            videoStream.getDecoder().decode(picture, null, 0);
            if (picture.isComplete()) {
                image = displayVideoAtCorrectTime(streamStartTime, picture, converter,
                        image, imageOutput, systemStartTime, systemTimeBase, streamTimebase);
                previousStreamEndTime = picture.getTimeStamp();
            }
        } while (picture.isComplete());
    }

    public void close()  {
        try {
            imageOutput.close();
            demuxer.close();
        } catch (InterruptedException | IOException e) {
            log.warn("Failed to close video", e);
        }
    }

    private BufferedImage displayVideoAtCorrectTime(long streamStartTime,
                                                    final MediaPicture picture, final MediaPictureConverter converter,
                                                    BufferedImage image, ImageOutput imageOutput, long systemStartTime,
                                                    final Rational systemTimeBase, final Rational streamTimebase)
            throws InterruptedException {
        long streamTimestamp = picture.getTimeStamp();
        log.debug("streamStartTime: {}", Long.toString(streamStartTime));
        log.debug("streamTimestamp {}", Long.toString(streamTimestamp));

        // convert streamTimestamp into system units (i.e. nano-seconds)
        log.debug("systemTimeBase {}",systemTimeBase);
        long relativeStreamTimestamp = systemTimeBase.rescale(streamTimestamp - streamStartTime, streamTimebase);
        log.debug("relativeStreamTimestamp {}",Long.toString(relativeStreamTimestamp));
        log.debug("systemStartTime {}",systemStartTime);
        long targetSystemTimestamp = systemStartTime + relativeStreamTimestamp;
        log.debug("targetSystemTimestamp {}",Long.toString(targetSystemTimestamp));
        timeSource.wakeUpAt(targetSystemTimestamp, TimeUnit.NANOSECONDS);

        // Convert the image from Humble format into Java images.
        image = converter.toImage(image, picture);
        imageOutput.writeImage(image);
        return image;
    }

    private static Optional<DemuxerStream> getFirstVideoStreamFrom(Demuxer demuxer) throws InterruptedException, IOException {
        int numStreams = demuxer.getNumStreams();
        for (int i = 0; i < numStreams; i++) {
            DemuxerStream currentStream = demuxer.getStream(i);
            final Decoder decoder = currentStream.getDecoder();
            if (decoder != null && decoder.getCodecType() == MediaDescriptor.Type.MEDIA_VIDEO) {
                return Optional.of(currentStream);
            }
        }

        return Optional.empty();
    }

    private static class LocalLoggerAdapter {
        void debug(String pattern, Object value) {
            LOG.log(Level.FINE, pattern, value);
        }

        void warn(String message, Exception e) {
            LOG.log(Level.SEVERE, message, e);
        }
    }
}
