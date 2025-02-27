package com.redhat.service.bridge.shard.operator;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.service.bridge.infra.models.dto.ProcessorDTO;
import com.redhat.service.bridge.shard.operator.providers.CustomerNamespaceProvider;
import com.redhat.service.bridge.shard.operator.providers.KafkaConfigurationCostants;
import com.redhat.service.bridge.shard.operator.providers.KafkaConfigurationProvider;
import com.redhat.service.bridge.shard.operator.providers.TemplateProvider;
import com.redhat.service.bridge.shard.operator.resources.BridgeExecutor;
import com.redhat.service.bridge.shard.operator.utils.Constants;
import com.redhat.service.bridge.shard.operator.utils.LabelsBuilder;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;

@ApplicationScoped
public class BridgeExecutorServiceImpl implements BridgeExecutorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeExecutorServiceImpl.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    CustomerNamespaceProvider customerNamespaceProvider;

    @Inject
    TemplateProvider templateProvider;

    @Inject
    KafkaConfigurationProvider kafkaConfigurationProvider;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "event-bridge.executor.image")
    String executorImage;

    @Override
    public void createBridgeExecutor(ProcessorDTO processorDTO) {
        final Namespace namespace = customerNamespaceProvider.fetchOrCreateCustomerNamespace(processorDTO.getCustomerId());

        kubernetesClient
                .resources(BridgeExecutor.class)
                .inNamespace(namespace.getMetadata().getName())
                .create(BridgeExecutor.fromDTO(processorDTO, namespace.getMetadata().getName(), executorImage));
    }

    // TODO: if the retrieved resource spec is not equal to the expected one, we should redeploy https://issues.redhat.com/browse/MGDOBR-140
    @Override
    public Deployment fetchOrCreateBridgeExecutorDeployment(BridgeExecutor bridgeExecutor) {
        Deployment deployment = kubernetesClient.apps().deployments().inNamespace(bridgeExecutor.getMetadata().getNamespace()).withName(bridgeExecutor.getMetadata().getName()).get();

        if (deployment != null) {
            return deployment;
        }

        deployment = templateProvider.loadBridgeExecutorDeploymentTemplate(bridgeExecutor);

        // Specs
        deployment.getSpec().getSelector().setMatchLabels(new LabelsBuilder().withAppInstance(bridgeExecutor.getMetadata().getName()).build());
        deployment.getSpec().getTemplate().getMetadata().setLabels(new LabelsBuilder().withAppInstance(bridgeExecutor.getMetadata().getName()).build());
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setName(BridgeExecutor.COMPONENT_NAME);
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(bridgeExecutor.getSpec().getImage());

        // TODO: All the Executor applications will push events to the same kafka cluster under the same kafka topic. This configuration will have to be specified by the manager for each Bridge instance: https://issues.redhat.com/browse/MGDOBR-123
        List<EnvVar> environmentVariables = new ArrayList<>();
        environmentVariables.add(new EnvVarBuilder().withName(KafkaConfigurationCostants.KAFKA_BOOTSTRAP_SERVERS_ENV_VAR).withValue(kafkaConfigurationProvider.getBootstrapServers()).build());
        environmentVariables.add(new EnvVarBuilder().withName(KafkaConfigurationCostants.KAFKA_CLIENT_ID_ENV_VAR).withValue(kafkaConfigurationProvider.getClient()).build());
        environmentVariables.add(new EnvVarBuilder().withName(KafkaConfigurationCostants.KAFKA_CLIENT_SECRET_ENV_VAR).withValue(kafkaConfigurationProvider.getSecret()).build());
        environmentVariables.add(new EnvVarBuilder().withName(KafkaConfigurationCostants.KAFKA_SECURITY_PROTOCOL_ENV_VAR).withValue(kafkaConfigurationProvider.getSecurityProtocol()).build());
        // Every Processor will subscribe with a new GROUP_ID, so that it will consume all the messages on the configured topic
        environmentVariables.add(new EnvVarBuilder().withName(KafkaConfigurationCostants.KAFKA_GROUP_ID_ENV_VAR).withValue(bridgeExecutor.getSpec().getId()).build());
        try {
            environmentVariables.add(new EnvVarBuilder().withName(Constants.BRIDGE_EXECUTOR_PROCESSOR_DEFINITION_ENV_VAR).withValue(objectMapper.writeValueAsString(bridgeExecutor.toDTO())).build());
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not serialize Processor Definition while setting executor deployment environment variables", e);
        }
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(environmentVariables);

        return kubernetesClient.apps().deployments().inNamespace(bridgeExecutor.getMetadata().getNamespace()).create(deployment);
    }

    // TODO: if the retrieved resource spec is not equal to the expected one, we should redeploy https://issues.redhat.com/browse/MGDOBR-140
    @Override
    public Service fetchOrCreateBridgeExecutorService(BridgeExecutor bridgeExecutor, Deployment deployment) {
        Service service = kubernetesClient.services().inNamespace(bridgeExecutor.getMetadata().getNamespace()).withName(bridgeExecutor.getMetadata().getName()).get();

        if (service != null) {
            return service;
        }

        service = templateProvider.loadBridgeExecutorServiceTemplate(bridgeExecutor);

        // Specs
        service.getSpec().setSelector(new LabelsBuilder().withAppInstance(deployment.getMetadata().getName()).build());

        return kubernetesClient.services().inNamespace(bridgeExecutor.getMetadata().getNamespace()).create(service);
    }

    @Override
    public void deleteBridgeExecutor(ProcessorDTO processorDTO) {
        final String namespace = customerNamespaceProvider.resolveName(processorDTO.getCustomerId());
        final boolean bridgeDeleted =
                kubernetesClient
                        .resources(BridgeExecutor.class)
                        .inNamespace(namespace)
                        .delete(BridgeExecutor.fromDTO(processorDTO, namespace, executorImage));
        if (bridgeDeleted) {
            customerNamespaceProvider.deleteCustomerNamespaceIfEmpty(processorDTO.getCustomerId());
        } else {
            // TODO: we might need to review this use case and have a manager to look at a queue of objects not deleted and investigate. Unfortunately the API does not give us a reason.
            LOGGER.warn("BridgeExecutor '{}' not deleted", processorDTO);
        }
    }
}
