package tdl.datapoint.video.support;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SNSEvent {

    private final String message;

    public SNSEvent(String message) {
        this.message = message;
    }

    public ObjectNode asJsonNode() {
        final JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode rootNode = factory.objectNode();
        ObjectNode sns = rootNode.putArray("Records").addObject().putObject("Sns");
        sns.put("Message", message);
        return rootNode;
    }
}
