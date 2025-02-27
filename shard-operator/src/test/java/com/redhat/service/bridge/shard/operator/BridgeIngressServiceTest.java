package com.redhat.service.bridge.shard.operator;

import java.time.Duration;
import java.util.List;

import javax.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.redhat.service.bridge.infra.models.dto.BridgeDTO;
import com.redhat.service.bridge.shard.operator.providers.CustomerNamespaceProvider;
import com.redhat.service.bridge.shard.operator.providers.KafkaConfigurationCostants;
import com.redhat.service.bridge.shard.operator.resources.BridgeIngress;
import com.redhat.service.bridge.shard.operator.utils.KubernetesResourcePatcher;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithOpenShiftTestServer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@QuarkusTest
@WithOpenShiftTestServer
public class BridgeIngressServiceTest {

    @Inject
    BridgeIngressService bridgeIngressService;

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    CustomerNamespaceProvider customerNamespaceProvider;

    @Inject
    KubernetesResourcePatcher kubernetesResourcePatcher;

    @BeforeEach
    public void setup() {
        // Kubernetes Server must be cleaned up at startup of every test.
        kubernetesResourcePatcher.cleanUp();
    }

    @Test
    public void testBridgeIngressCreation() {
        // Given
        BridgeDTO dto = TestSupport.newProvisioningBridgeDTO();

        // When
        bridgeIngressService.createBridgeIngress(dto);

        // Then
        BridgeIngress bridgeIngress = kubernetesClient
                .resources(BridgeIngress.class)
                .inNamespace(customerNamespaceProvider.resolveName(dto.getCustomerId()))
                .withName(BridgeIngress.resolveResourceName(dto.getId()))
                .get();
        assertThat(bridgeIngress).isNotNull();
    }

    @Test
    public void testBridgeIngressCreationTriggersController() {
        // Given
        BridgeDTO dto = TestSupport.newProvisioningBridgeDTO();

        // When
        bridgeIngressService.createBridgeIngress(dto);

        // Then
        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            // The deployment is deployed by the controller
                            Deployment deployment = kubernetesClient.apps().deployments()
                                    .inNamespace(customerNamespaceProvider.resolveName(dto.getCustomerId()))
                                    .withName(BridgeIngress.resolveResourceName(dto.getId()))
                                    .get();
                            assertThat(deployment).isNotNull();
                            List<EnvVar> environmentVariables = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
                            assertThat(environmentVariables.stream().filter(x -> x.getName().equals(KafkaConfigurationCostants.KAFKA_BOOTSTRAP_SERVERS_ENV_VAR)).findFirst().get().getValue().length())
                                    .isGreaterThan(0);
                            assertThat(environmentVariables.stream().filter(x -> x.getName().equals(KafkaConfigurationCostants.KAFKA_CLIENT_ID_ENV_VAR)).findFirst().get().getValue().length())
                                    .isGreaterThan(0);
                            assertThat(environmentVariables.stream().filter(x -> x.getName().equals(KafkaConfigurationCostants.KAFKA_CLIENT_SECRET_ENV_VAR)).findFirst().get().getValue().length())
                                    .isGreaterThan(0);
                            assertThat(environmentVariables.stream().filter(x -> x.getName().equals(KafkaConfigurationCostants.KAFKA_SECURITY_PROTOCOL_ENV_VAR)).findFirst().get().getValue().length())
                                    .isGreaterThan(0);
                        });
    }

    @Test
    public void testBridgeIngressDeletion() {
        // Given
        BridgeDTO dto = TestSupport.newProvisioningBridgeDTO();

        // When
        bridgeIngressService.createBridgeIngress(dto);
        bridgeIngressService.deleteBridgeIngress(dto);

        // Then
        BridgeIngress bridgeIngress = kubernetesClient
                .resources(BridgeIngress.class)
                .inNamespace(customerNamespaceProvider.resolveName(dto.getCustomerId()))
                .withName(BridgeIngress.resolveResourceName(dto.getId()))
                .get();
        assertThat(bridgeIngress).isNull();
    }

    @Test
    @Disabled("Delete loop in BridgeIngressController does not get called. Bug in the SDK? https://issues.redhat.com/browse/MGDOBR-128")
    public void testBridgeIngressDeletionRemovesAllLinkedResource() {
        // Given
        BridgeDTO dto = TestSupport.newProvisioningBridgeDTO();

        // When
        bridgeIngressService.createBridgeIngress(dto);
        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            Deployment deployment = kubernetesClient.apps().deployments()
                                    .inNamespace(customerNamespaceProvider.resolveName(dto.getCustomerId()))
                                    .withName(BridgeIngress.resolveResourceName(dto.getId()))
                                    .get();
                            assertThat(deployment).isNotNull();
                        });
        bridgeIngressService.deleteBridgeIngress(dto);

        // Then
        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            Deployment deployment = kubernetesClient.apps().deployments()
                                    .inNamespace(customerNamespaceProvider.resolveName(dto.getCustomerId()))
                                    .withName(BridgeIngress.resolveResourceName(dto.getId()))
                                    .get();
                            assertThat(deployment).isNull();
                        });
    }
}
