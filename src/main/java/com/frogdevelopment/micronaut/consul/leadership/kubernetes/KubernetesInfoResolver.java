package com.frogdevelopment.micronaut.consul.leadership.kubernetes;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

import jakarta.inject.Singleton;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.kubernetes.client.openapi.config.KubeConfig;
import io.micronaut.kubernetes.client.openapi.config.model.Cluster;
import io.micronaut.kubernetes.client.openapi.resolver.NamespaceResolver;
import io.micronaut.kubernetes.client.openapi.resolver.PodNameResolver;

@Singleton
@Requires(env = Environment.KUBERNETES)
@RequiredArgsConstructor
public class KubernetesInfoResolver {

    private final PodNameResolver podNameResolver;
    private final NamespaceResolver namespaceResolver;
    private final KubeConfig kubeConfig;

    public Optional<String> resolvePodName() {
        return podNameResolver.getPodName();
    }

    public Optional<String> resolveNamespace() {
        return Optional.ofNullable(namespaceResolver.resolveNamespace());
    }

    public Optional<String> resolveClusterName() {
        return Optional.ofNullable(kubeConfig.getCluster()).map(Cluster::server);
    }
}
