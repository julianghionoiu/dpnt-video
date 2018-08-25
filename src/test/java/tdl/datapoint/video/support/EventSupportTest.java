package tdl.datapoint.video.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class EventSupportTest {


    private ObjectMapper objectMapper;


    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void correctly_create_s3_event() throws JsonProcessingException {
        S3Event s3Event = new S3Event("my_bucket", "my_key");

        String output = objectMapper.writeValueAsString(s3Event.asJsonNode());

        assertThat(output, equalTo("{\"Records\":[{\"s3\":" +
                "{\"bucket\":{\"name\":\"my_bucket\"}," +
                "\"object\":{\"key\":\"my_key\"}}}" +
                "]}"));
    }


    @Test
    public void correctly_create_sns_event() throws JsonProcessingException {
        SNSEvent snsEvent = new SNSEvent("{ \"key\": \"value\" }");

        String output = objectMapper.writeValueAsString(snsEvent.asJsonNode());

        assertThat(output, equalTo("{\"Records\":[{\"Sns\":" +
                "{\"Message\":\"{ \\\"key\\\": \\\"value\\\" }\"}}" +
                "]}"));
    }
}
