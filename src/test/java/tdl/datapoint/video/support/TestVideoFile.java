package tdl.datapoint.video.support;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@SuppressWarnings({"WeakerAccess", "unused"})
public class TestVideoFile {
    private final Path resourcePath;

    public TestVideoFile(String name) {
        resourcePath = Paths.get("src/test/resources/", name);
    }

    public File asFile() {
        return resourcePath.toFile();
    }
}
