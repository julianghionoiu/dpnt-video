package tdl.datapoint.video.support;

import tdl.record.sourcecode.snapshot.file.Reader;
import tdl.record.sourcecode.snapshot.file.Segment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings({"WeakerAccess", "unused"})
public class TestVideoFile {
    private final Path resourcePath;

    public TestVideoFile(String name) {
        resourcePath = Paths.get("src/test/resources/", name);
    }

    public File asFile() {
        return resourcePath.toFile();
    }

    public List<String> getTags() throws IOException {
        Reader reader = new Reader(asFile());
        List<String> tags = new ArrayList<>();
        while (reader.hasNext()) {
            Segment segment = reader.nextSegment();
            String tag = segment.getTag().trim();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    public List<String> getTags(Predicate<? super String> predicate) throws IOException {
        return getTags().stream().filter(predicate).collect(Collectors.toList());
    }
}
