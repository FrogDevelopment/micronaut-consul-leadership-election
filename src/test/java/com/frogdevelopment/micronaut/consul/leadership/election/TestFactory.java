package com.frogdevelopment.micronaut.consul.leadership.election;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import jakarta.inject.Singleton;

import org.mockito.Mockito;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.kubernetes.client.openapi.config.DefaultKubeConfigLoader;
import io.micronaut.kubernetes.client.openapi.config.KubeConfig;
import io.micronaut.kubernetes.client.openapi.config.KubeConfigLoader;
import io.micronaut.kubernetes.client.openapi.config.model.Cluster;
import io.micronaut.kubernetes.client.openapi.resolver.NamespaceResolver;
import io.micronaut.kubernetes.client.openapi.resolver.PodNameResolver;

@Factory
@Requires(env = "mocked")
public class TestFactory {

    @Primary
    @Singleton
    PodNameResolver podNameResolver(@Value("${mock.pod-name") final String podName) {
        return () -> Optional.of(podName);
    }

    @Primary
    @Singleton
    NamespaceResolver namespaceResolver(@Value("${mock.namespace") final String namespace) {
        return () -> namespace;
    }

    @Singleton
    @Replaces(DefaultKubeConfigLoader.class)
    KubeConfig kubeConfig(@Value("${mock.cluster") final String clusterName) {
        final var kubeConfig = mock(KubeConfig.class);
        final var cluster = mock(Cluster.class);
        given(kubeConfig.getCluster()).willReturn(cluster);
        given(cluster.server()).willReturn(clusterName);
        return kubeConfig;
    }

    @Singleton
    @Replaces(DefaultKubeConfigLoader.class)
    KubeConfigLoader kubeConfigLoader(final KubeConfig kubeConfig) {
        final var kubeConfigLoader = Mockito.mock(KubeConfigLoader.class);
        given(kubeConfigLoader.getKubeConfig()).willReturn(kubeConfig);
        return kubeConfigLoader;
    }
}
