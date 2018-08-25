package tdl.datapoint.video.processing;

import com.amazonaws.services.s3.model.S3Object;
import org.apache.commons.io.FileUtils;
import tdl.record.sourcecode.snapshot.file.ToGitConverter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class S3SrcsToGitExporter {

    public void export(S3Object s3Object, Path outputDir) throws Exception {
        Path inputFile = downloadObject(s3Object);
        ToGitConverter converter = new ToGitConverter(inputFile, outputDir);
        converter.convert();
    }

    private Path downloadObject(S3Object s3Object) throws IOException {
        File file = File.createTempFile("code_", ".srcs");
        InputStream source = s3Object.getObjectContent();
        FileUtils.copyInputStreamToFile(source, file);
        return file.toPath();
    }
}
