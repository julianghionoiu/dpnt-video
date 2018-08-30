package tdl.datapoint.video.support;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    
    public File getS3Object(String prefix, String suffix) throws IOException {
        InputStream s3ObjectInputStream = s3Bucket.getObject(s3Key).getObjectContent();
        File tmpFile = File.createTempFile(prefix, suffix);
        Files.copy(s3ObjectInputStream, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return tmpFile;
    }
}
