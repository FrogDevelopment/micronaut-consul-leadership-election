package com.frogdevelopment.micronaut.consul.leadership.kubernetes;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micronaut.kubernetes.client.openapi.api.CoreV1Api;
import io.micronaut.kubernetes.client.openapi.model.V1ObjectMeta;
import io.micronaut.kubernetes.client.openapi.model.V1Pod;

@ExtendWith(MockitoExtension.class)
class UpdatePodLabelTest {

    @InjectMocks
    private UpdatePodLabel updatePodLabel;

    @Mock
    private CoreV1Api coreV1Api;
    @Mock
    private KubernetesInfoResolver kubernetesInfoResolver;
    @Mock
    private LeadershipPodLabelConfiguration leadershipPodLabelConfiguration;
    @Mock
    private V1Pod v1Pod;
    @Mock
    private V1ObjectMeta metadata;

    @Test
    void should_updatePodLabel_leader() {
        // given
        given(kubernetesInfoResolver.resolveNamespace()).willReturn(Optional.of("my-namespace"));
        given(kubernetesInfoResolver.resolvePodName()).willReturn(Optional.of("my-podname"));
        given(leadershipPodLabelConfiguration.getLabelForLeader()).willReturn("leader");
        given(leadershipPodLabelConfiguration.getKey()).willReturn("key");
        given(coreV1Api.patchNamespacedPod("my-podname", "my-namespace", "{\"metadata\":{\"labels\":{\"key\":\"leader\"}}}", null, null, null, null, null)).willReturn(v1Pod);

        // when
        updatePodLabel.updatePodLabel(true);

        // then
        then(coreV1Api).should().patchNamespacedPod("my-podname", "my-namespace", "{\"metadata\":{\"labels\":{\"key\":\"leader\"}}}", null, null, null, null, null);
        then(leadershipPodLabelConfiguration).shouldHaveNoMoreInteractions();
    }

    @Test
    void should_updatePodLabel_follower() {
        // given
        given(kubernetesInfoResolver.resolveNamespace()).willReturn(Optional.of("my-namespace"));
        given(kubernetesInfoResolver.resolvePodName()).willReturn(Optional.of("my-podname"));
        given(leadershipPodLabelConfiguration.getLabelForFollower()).willReturn("follower");
        given(leadershipPodLabelConfiguration.getKey()).willReturn("key");
        given(coreV1Api.patchNamespacedPod("my-podname", "my-namespace", "{\"metadata\":{\"labels\":{\"key\":\"follower\"}}}", null, null, null, null, null)).willReturn(v1Pod);

        // when
        updatePodLabel.updatePodLabel(false);

        // then
        then(coreV1Api).should().patchNamespacedPod("my-podname", "my-namespace", "{\"metadata\":{\"labels\":{\"key\":\"follower\"}}}", null, null, null, null, null);
        then(leadershipPodLabelConfiguration).shouldHaveNoMoreInteractions();
    }

    @Test
    void should_quietlyHandleError() {
        // given
        given(kubernetesInfoResolver.resolveNamespace()).willReturn(Optional.of("my-namespace"));
        given(kubernetesInfoResolver.resolvePodName()).willReturn(Optional.of("my-podname"));
        given(leadershipPodLabelConfiguration.getKey()).willReturn("key");
        given(leadershipPodLabelConfiguration.getLabelForFollower()).willReturn("follower");
        given(coreV1Api.patchNamespacedPod("my-podname", "my-namespace", "{\"metadata\":{\"labels\":{\"key\":\"follower\"}}}", null, null, null, null, null))
                .willThrow(new RuntimeException("boom"));

        // then
        assertThatNoException().isThrownBy(() -> updatePodLabel.updatePodLabel(false));
    }
}
