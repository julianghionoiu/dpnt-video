package tdl.datapoint.video.support;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.io.File;

public class LocalS3Bucket {
    private final AmazonS3 s3Client;
    private final String bucket;

    private LocalS3Bucket(AmazonS3 s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public static LocalS3Bucket createInstance(String endpoint, String region, String bucket) {
        AwsClientBuilder.EndpointConfiguration endpointConfiguration =
                new AwsClientBuilder.EndpointConfiguration(endpoint, region);
        AmazonS3 s3Client = AmazonS3ClientBuilder
                .standard()
                .withPathStyleAccessEnabled(true)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .withEndpointConfiguration(endpointConfiguration)
                .build();
        return new LocalS3Bucket(s3Client, bucket);
    }

    public S3Event putObject(File object, String key) {
        createBucketIfNotExists(s3Client, bucket);
        s3Client.putObject(bucket, key, object);
        return new S3Event(bucket, key);
    }

    @SuppressWarnings("deprecation")
    private void createBucketIfNotExists(AmazonS3 client, String bucket) {
        if (!client.doesBucketExist(bucket)) {
            client.createBucket(bucket);
        }
    }
}
