package tdl.datapoint.video.barcode;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import tdl.datapoint.video.output.ImageOutput;
import tdl.datapoint.video.time.TimeSource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class OutputToBarcodeMatrixReader implements ImageOutput {

    private final TimeSource timeSource;
    private final List<TimestampedPayload> decodedBarcodes;

    public OutputToBarcodeMatrixReader(TimeSource timeSource) {
        this.timeSource = timeSource;
        this.decodedBarcodes = new ArrayList<>();
    }

    @Override
    public void open() {
    }

    @Override
    public BufferedImage getSuggestedOutputSample(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
    }

    @Override
    public void writeImage(BufferedImage image) {
        long systemTimeNano = timeSource.currentTimeNano();

        try {
            int subWidth = image.getWidth()/2;
            int subHeight = image.getHeight()/2;
            String topLeftPayload = decodeBarcode(
                    image.getSubimage(0, 0, subWidth, subHeight),
                    BarcodeFormat.CODE_39);
            String topRightPayload = decodeBarcode(
                    image.getSubimage(subWidth, 0, subWidth, subHeight),
                    BarcodeFormat.QR_CODE);
            String bottomLeftPayload = decodeBarcode(
                    image.getSubimage(0, subHeight, subWidth, subHeight),
                    BarcodeFormat.QR_CODE);
            String bottomRightPayload = decodeBarcode(
                    image.getSubimage(subWidth, subHeight, subWidth, subHeight),
                    BarcodeFormat.CODE_39);

            decodedBarcodes.add(new TimestampedPayload(systemTimeNano,
                    topLeftPayload,
                    topRightPayload,
                    bottomLeftPayload,
                    bottomRightPayload));
        } catch (IOException e) {
            System.err.println("Could not extract barcode at: " + systemTimeNano);
        }
    }

    @Override
    public void close() {
        //Nothing to close
    }

    public List<TimestampedPayload> getDecodedBarcodes() {
        return decodedBarcodes;
    }

    public static class TimestampedPayload {

        public final Long videoTimestamp;
        public final String topLeftPayload;
        public final String topRightPayload;
        public final String bottomLeftPayload;
        public final String bottomRightPayload;

        TimestampedPayload(Long videoTimestamp,
                           String topLeftPayload,
                           String topRightPayload,
                           String bottomLeftPayload,
                           String bottomRightPayload) {
            this.videoTimestamp = videoTimestamp;
            this.topLeftPayload = topLeftPayload;
            this.topRightPayload = topRightPayload;
            this.bottomLeftPayload = bottomLeftPayload;
            this.bottomRightPayload = bottomRightPayload;
        }

        @Override
        public String toString() {
            return "TimestampedPayloads{" +
                    "videoTimestamp=" + videoTimestamp +
                    ", topLeftPayload='" + topLeftPayload + '\'' +
                    ", topRightPayload='" + topRightPayload + '\'' +
                    ", bottomLeftPayload='" + bottomLeftPayload + '\'' +
                    ", bottomRightPayload='" + bottomRightPayload + '\'' +
                    '}';
        }
    }

    private static String decodeBarcode(BufferedImage image, final BarcodeFormat format)
            throws IOException {
        ArrayList<BarcodeFormat> barcodeFormats = new ArrayList<BarcodeFormat>() {
            {
                add(format);
            }
        };
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, barcodeFormats);

        LuminanceSource source;
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(),
                null, 0, image.getWidth());
        source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        MultiFormatReader multiFormatReader = new MultiFormatReader();
        try {
            return multiFormatReader.decode(bitmap, hints).getText();
        } catch (NotFoundException e) {
            return "";
        }
    }
}
