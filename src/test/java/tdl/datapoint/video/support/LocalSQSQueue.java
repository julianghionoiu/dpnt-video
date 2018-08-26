package tdl.datapoint.video.support;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import org.hamcrest.CoreMatchers;
import tdl.participant.queue.connector.QueueSize;
import tdl.participant.queue.connector.SqsEventQueue;

import static org.hamcrest.MatcherAssert.assertThat;

public class LocalSQSQueue {
    public static SqsEventQueue createInstance(String endpoint,
                                               String region,
                                               String queueUrl) {
        AwsClientBuilder.EndpointConfiguration endpointConfiguration =
                new AwsClientBuilder.EndpointConfiguration(endpoint, region);
        AmazonSQS client = AmazonSQSClientBuilder.standard()
                .withEndpointConfiguration(endpointConfiguration)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

        client.purgeQueue(new PurgeQueueRequest(queueUrl));
        SqsEventQueue sqsEventQueue = new SqsEventQueue(client, queueUrl);
        assertThat("Queue " + queueUrl + " is not clean.", sqsEventQueue.getQueueSize(),
                CoreMatchers.is(new QueueSize(0, 0, 0)));
        return sqsEventQueue;
    }
}
