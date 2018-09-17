package tdl.datapoint.video.processing;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.AwsVpcConfiguration;
import com.amazonaws.services.ecs.model.ContainerOverride;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.NetworkConfiguration;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.TaskOverride;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ECSVideoTaskRunner {
    private static final Logger LOG = Logger.getLogger(ECSVideoTaskRunner.class.getName());

    private final String taskDefinitionPrefix;
    private final Supplier<RunTaskRequest> runTaskRequestSupplier;
    private final AmazonECS ecsClient;

    public ECSVideoTaskRunner(AmazonECS ecsClient,
                              String cluster,
                              String taskDefinitionPrefix,
                              String launchType,
                              String subnet,
                              String securityGroup,
                              String assignPublicIp) {
        this.ecsClient = ecsClient;
        this.taskDefinitionPrefix = taskDefinitionPrefix;

        runTaskRequestSupplier = () -> {
            RunTaskRequest runTaskRequest = new RunTaskRequest();
            runTaskRequest.setCluster(cluster);
            runTaskRequest.setTaskDefinition(ECSVideoTaskRunner.this.taskDefinitionPrefix + "notset");
            runTaskRequest.setLaunchType(launchType);

            NetworkConfiguration networkConfiguration = new NetworkConfiguration();
            AwsVpcConfiguration awsvpcConfiguration = new AwsVpcConfiguration();
            awsvpcConfiguration.setSubnets(Collections.singletonList(subnet));
            awsvpcConfiguration.setSecurityGroups(Collections.singletonList(securityGroup));
            awsvpcConfiguration.setAssignPublicIp(assignPublicIp);

            networkConfiguration.setAwsvpcConfiguration(awsvpcConfiguration);
            runTaskRequest.setNetworkConfiguration(networkConfiguration);
            return runTaskRequest;
        };
    }

    public void runVideoTask(String participantId, String challengeId,
                             String s3UrlNewVideo, String s3UrlAccumulatorVideo,
                             String videoPublishDestinationUrl) {
        RunTaskRequest runTaskRequest = runTaskRequestSupplier.get();
        runTaskRequest.setTaskDefinition(this.taskDefinitionPrefix);

        HashMap<String, String> env = new HashMap<>();
        env.put("PARTICIPANT_ID", participantId);
        env.put("CHALLENGE_ID", challengeId);
        env.put("S3_URL_NEW_VIDEO", s3UrlNewVideo);
        env.put("S3_URL_ACCUMULATOR_VIDEO", s3UrlAccumulatorVideo);
        env.put("VIDEO_PUBLISH_DESTINATION_URL", videoPublishDestinationUrl);
        setTaskEnv(runTaskRequest, env);

        LOG.info("Issuing RunTask command: " + runTaskRequest);
        ecsClient.runTask(runTaskRequest);
    }

    private void setTaskEnv(RunTaskRequest runTaskRequest, HashMap<String, String> env) {
        TaskOverride overrides = new TaskOverride();
        ContainerOverride containerOverride = new ContainerOverride();
        containerOverride.setName("default-container");
        List<KeyValuePair> envPairs = env.entrySet().stream().map(entry -> new KeyValuePair()
                .withName(entry.getKey()).withValue(entry.getValue())).collect(Collectors.toList());
        containerOverride.setEnvironment(envPairs);
        overrides.setContainerOverrides(Collections.singletonList(containerOverride));
        runTaskRequest.setOverrides(overrides);
    }
}
