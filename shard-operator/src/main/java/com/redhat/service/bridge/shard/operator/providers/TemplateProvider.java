package com.redhat.service.bridge.shard.operator.providers;

import com.redhat.service.bridge.shard.operator.resources.BridgeExecutor;
import com.redhat.service.bridge.shard.operator.resources.BridgeIngress;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.openshift.api.model.Route;

public interface TemplateProvider {

    Deployment loadBridgeIngressDeploymentTemplate(BridgeIngress bridgeIngress);

    Service loadBridgeIngressServiceTemplate(BridgeIngress bridgeIngress);

    Deployment loadBridgeExecutorDeploymentTemplate(BridgeExecutor bridgeExecutor);

    Service loadBridgeExecutorServiceTemplate(BridgeExecutor bridgeExecutor);

    Route loadBridgeIngressOpenshiftRouteTemplate(BridgeIngress bridgeIngress);

    Ingress loadBridgeIngressKubernetesIngressTemplate(BridgeIngress bridgeIngress);
}
