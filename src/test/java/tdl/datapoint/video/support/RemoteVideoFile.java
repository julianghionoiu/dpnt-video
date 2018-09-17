package tdl.datapoint.video.support;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings({"WeakerAccess", "unused"})
public class RemoteVideoFile {
    private static final int CONNECT_TIMEOUT = 500;
    private static final int READ_TIMEOUT = 1000;

    private String sourceUrl;

    public RemoteVideoFile(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public Path download() throws IOException {
        String sourceURL = sourceUrl;
        Path targetDirectory = Files.createTempDirectory("tmp");
        String fileName = sourceURL.substring(sourceURL.lastIndexOf('/') + 1);
        File targetFile = new File(targetDirectory + File.separator + fileName);

        FileUtils.copyURLToFile(
                new URL(sourceUrl),
                targetFile,
                CONNECT_TIMEOUT,
                READ_TIMEOUT);

        return targetFile.toPath();
    }

    @Override
    public String toString() {
        return "RemoteVideoFile{" +
                "sourceUrl='" + sourceUrl + '\'' +
                '}';
    }
}
