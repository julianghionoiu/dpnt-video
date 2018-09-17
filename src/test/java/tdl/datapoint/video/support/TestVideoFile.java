package tdl.datapoint.video.support;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@SuppressWarnings({"WeakerAccess", "unused"})
public class TestVideoFile {
    private Path resourcePath;

    public TestVideoFile(String name) {
        resourcePath = Paths.get("src/test/resources/", name);
    }

    public File asFile() {
        return resourcePath.toFile();
    }

    public boolean isNotPresentOrIsEmpty() {
        boolean fileDoesNotExist = ! resourcePath.toFile().exists();
        boolean empty = resourcePath.toFile().length() == 0;
        return fileDoesNotExist || empty;
    }

    public boolean isPresentAndIsNotEmpty() {
        boolean fileExists = resourcePath.toFile().exists();
        boolean notEmpty = resourcePath.toFile().length() != 0;
        return fileExists && notEmpty;
    }
}
