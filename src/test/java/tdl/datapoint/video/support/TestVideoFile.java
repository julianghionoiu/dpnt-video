package tdl.datapoint.video.support;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@SuppressWarnings({"WeakerAccess", "unused"})
public class TestVideoFile {
    private Path resourcePath;

    private String name;

    private LocalS3Bucket s3Bucket;
    private String s3Key;

    public TestVideoFile(String name) {
        this.name = name;
        resourcePath = Paths.get("src/test/resources/", name);
    }

    public TestVideoFile(LocalS3Bucket s3Bucket, String key) {
        this.s3Bucket = s3Bucket;
        this.s3Key = key;
    }

    public File asFile() {
        return resourcePath.toFile();
    }
    
    public Path downloadFile() throws IOException {
        String sourceURL = name;
        Path targetDirectory = Files.createTempDirectory("tmp");

        URL url = new URL(sourceURL);
        String fileName = sourceURL.substring(sourceURL.lastIndexOf('/') + 1);
        Path targetPath = new File(targetDirectory + File.separator + fileName).toPath();
        Files.copy(url.openStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return targetPath;
    }
}
