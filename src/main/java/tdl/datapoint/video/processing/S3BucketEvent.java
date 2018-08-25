package tdl.datapoint.video.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class S3BucketEvent {
    private String bucket;

    private String key;

    private S3BucketEvent(String bucket, String key) {
        this.bucket = bucket;
        this.key = key;
    }

    @SuppressWarnings("unchecked")
    public static S3BucketEvent from(Map<String, Object> request,
                                     ObjectMapper jsonObjectMapper) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("No input provided");
        }

        Map<String, Object> record = ((List<Map<String, Object>>) mapGet(request, "Records")).get(0);
        Map<String, Object> sns = (Map<String, Object>) mapGet(record, "Sns");
        String jsonS3Payload = (String) mapGet(sns, "Message");


        JsonNode s3EventTree = jsonObjectMapper.readTree(jsonS3Payload);
        JsonNode s3Object = s3EventTree.get("Records").get(0).get("s3");

        String bucket = s3Object.get("bucket").get("name").asText();
        String key = s3Object.get("object").get("key").asText();
        return new S3BucketEvent(bucket, key);
    }

    private static Object mapGet(Map<String, Object> map, String key) {
        if (map == null) {
            throw new IllegalArgumentException("No input provided. Map is \"null\".");
        }

        Object o = map.get(key);
        if (o == null) {
            throw new IllegalArgumentException(String.format("Key \"%s\" not found in map.", key));
        }
        return o;
    }

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
    }

    public String getChallengeId() {
        return key.split("/")[0];
    }

    public String getParticipantId() {
        return key.split("/")[1];
    }

    @Override
    public String toString() {
        return "S3BucketEvent{" +
                "bucket='" + bucket + '\'' +
                ", key='" + key + '\'' +
                '}';
    }
}
