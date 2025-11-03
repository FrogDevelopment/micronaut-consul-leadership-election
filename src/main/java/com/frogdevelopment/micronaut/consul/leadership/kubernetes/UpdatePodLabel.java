package com.frogdevelopment.micronaut.consul.leadership.kubernetes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.kubernetes.client.openapi.api.CoreV1Api;

/**
 * Updates Kubernetes pod labels based on leadership status.
 * <p>
 * This component listens to leadership changes and updates the current pod's labels
 * to reflect whether it is the leader or a follower. This is useful for Kubernetes
 * operators, monitoring systems, or service meshes that need to identify the leader pod.
 * </p>
 *
 * @since 1.0.0
 */
@Slf4j
@Singleton
@Requires(env = Environment.KUBERNETES)
@Requires(property = LeadershipPodLabelConfiguration.PREFIX + ".enabled",
          notEquals = StringUtils.FALSE,
          defaultValue = StringUtils.TRUE)
@RequiredArgsConstructor
public class UpdatePodLabel {

    private final CoreV1Api coreV1Api;
    private final KubernetesInfoResolver kubernetesInfoResolver;
    private final LeadershipPodLabelConfiguration leadershipPodLabelConfiguration;

    /**
     * Updates the pod label to reflect current leadership status.
     * <p>
     * This method reads the current pod, updates its label, and persists the changes
     * back to Kubernetes. If any error occurs (e.g., insufficient permissions, network
     * issues), the error is logged but not propagated to avoid impacting the leadership
     * election process.
     * </p>
     *
     * @param isLeader {@code true} if this pod is the leader, {@code false} otherwise
     */
    public void updatePodLabel(final boolean isLeader) {
        String namespace = null;
        String podName = null;
        try {
            namespace = kubernetesInfoResolver.resolveNamespace().orElseThrow(
                    () -> new IllegalStateException("Unable to resolve namespace"));
            podName = kubernetesInfoResolver.resolvePodName().orElseThrow(
                    () -> new IllegalStateException("Unable to resolve pod name"));
            final var labelKey = leadershipPodLabelConfiguration.getKey();
            final var labelValue = isLeader
                    ? leadershipPodLabelConfiguration.getLabelForLeader()
                    : leadershipPodLabelConfiguration.getLabelForFollower();

            log.info("Updating pod label for namespace: {}, podName: {}, {}={}",
                    namespace, podName, labelKey, labelValue);

            // Create a strategic merge patch to update only the label
            final var patchJson = String.format(
                    "{\"metadata\":{\"labels\":{\"%s\":\"%s\"}}}",
                    labelKey, labelValue
            );

            // Apply patch to Kubernetes
            coreV1Api.patchNamespacedPod(podName, namespace, patchJson, null, null, null, null, null);

            log.debug("Successfully updated pod label {}={} for pod {}/{}",
                    labelKey, labelValue, namespace, podName);
        } catch (final Exception e) {
            log.error("Failed to update pod label for pod {}/{}: {}",
                    namespace, podName, e.getMessage(), e);
        }
    }
}
