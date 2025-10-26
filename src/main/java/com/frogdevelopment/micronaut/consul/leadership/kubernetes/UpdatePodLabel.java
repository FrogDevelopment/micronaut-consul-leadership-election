package com.frogdevelopment.micronaut.consul.leadership.kubernetes;

import lombok.RequiredArgsConstructor;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.kubernetes.client.openapi.api.CoreV1Api;
import io.micronaut.kubernetes.client.openapi.resolver.NamespaceResolver;
import io.micronaut.kubernetes.client.openapi.resolver.PodNameResolver;

@Singleton
@Requires(env = Environment.KUBERNETES)
@Requires(property = LeadershipConfiguration.PREFIX + ".update-pod-label",
          notEquals = StringUtils.FALSE,
          defaultValue = StringUtils.TRUE)
@RequiredArgsConstructor
public class UpdatePodLabel {

    private final CoreV1Api coreV1Api;
    private final NamespaceResolver namespaceResolver;
    private final PodNameResolver podNameResolver;

    public void updatePodLabel(final boolean isLeader) {
        final var namespace = namespaceResolver.resolveNamespace();
        final var podName = podNameResolver.getPodName().orElseThrow();
        final var v1Pod = coreV1Api.readNamespacedPod(namespace, podName, null);
        v1Pod.getMetadata().putLabelsItem("isLeader", String.valueOf(isLeader));
    }
}
