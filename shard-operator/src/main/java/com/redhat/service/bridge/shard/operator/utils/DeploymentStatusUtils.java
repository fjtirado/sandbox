package com.redhat.service.bridge.shard.operator.utils;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;

/**
 * @see <a href="https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#deployment-status">Deployment Status</a>
 */
public final class DeploymentStatusUtils {

    // https://pkg.go.dev/k8s.io/api/apps/v1#DeploymentConditionType

    private static final String REPLICA_FAILURE = "ReplicaFailure";
    private static final String STATUS_TRUE = "True";

    private DeploymentStatusUtils() {
    }

    public static boolean isStatusReplicaFailure(final Deployment d) {
        if (!hasValidConditions(d)) {
            return false;
        }
        return d.getStatus().getConditions().stream().anyMatch(c -> REPLICA_FAILURE.equalsIgnoreCase(c.getType()) && STATUS_TRUE.equalsIgnoreCase(c.getStatus()));
    }

    public static String getReasonAndMessageForReplicaFailure(final Deployment d) {
        return getReasonAndMessage(REPLICA_FAILURE, d);
    }

    private static String getReasonAndMessage(final String type, final Deployment d) {
        if (!hasValidConditions(d)) {
            return "";
        }
        final Optional<DeploymentCondition> condition = d.getStatus().getConditions().stream().filter(c -> type.equalsIgnoreCase(c.getType())).findFirst();
        if (condition.isPresent()) {
            return String.format("%s: %s", condition.get().getReason(), condition.get().getMessage());
        }
        return "";
    }

    private static boolean hasValidConditions(final Deployment d) {
        return d.getStatus() != null && d.getStatus().getConditions() != null;
    }
}
