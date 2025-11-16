package com.frogdevelopment.micronaut.consul.leadership.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micronaut.kubernetes.client.openapi.config.KubeConfig;
import io.micronaut.kubernetes.client.openapi.config.model.Cluster;
import io.micronaut.kubernetes.client.openapi.resolver.NamespaceResolver;
import io.micronaut.kubernetes.client.openapi.resolver.PodNameResolver;

@ExtendWith(MockitoExtension.class)
class KubernetesInfoResolverTest {

    @InjectMocks
    private KubernetesInfoResolver kubernetesInfoResolver;

    @Mock
    private PodNameResolver podNameResolver;
    @Mock
    private NamespaceResolver namespaceResolver;
    @Mock
    private KubeConfig kubeConfig;
    @Mock
    private Cluster cluster;

    @Test
    void should_resolvePodName() {
        // given
        given(podNameResolver.getPodName()).willReturn(Optional.of("my-podname"));

        // when
        final var resolved = kubernetesInfoResolver.resolvePodName();

        // then
        assertThat(resolved).isEqualTo(Optional.of("my-podname"));
    }

    @Test
    void should_resolveNamespace() {
        // given
        given(namespaceResolver.resolveNamespace()).willReturn("my-namespace");

        // when
        final var resolved = kubernetesInfoResolver.resolveNamespace();

        // then
        assertThat(resolved).isEqualTo(Optional.of("my-namespace"));
    }

    @Test
    void should_resolveClusterName() {
        // given
        given(kubeConfig.getCluster()).willReturn(cluster);
        given(cluster.server()).willReturn("my-cluster");

        // when
        final var resolved = kubernetesInfoResolver.resolveClusterName();

        // then
        assertThat(resolved).isEqualTo(Optional.of("my-cluster"));
    }

}
