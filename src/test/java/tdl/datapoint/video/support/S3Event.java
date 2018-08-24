package tdl.datapoint.video.support;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class S3Event {
    private final String bucket;
    private final String key;

    S3Event(String bucket, String key) {
        this.bucket = bucket;
        this.key = key;
    }

    public ObjectNode asJsonNode() {
        final JsonNodeFactory factory = JsonNodeFactory.instance;

        ObjectNode rootNode = factory.objectNode();
        ObjectNode s3 = rootNode.putArray("Records").addObject().putObject("s3");
        s3.putObject("bucket").put("name", bucket);
        s3.putObject("object").put("key", key);
        return rootNode;
    }
}
