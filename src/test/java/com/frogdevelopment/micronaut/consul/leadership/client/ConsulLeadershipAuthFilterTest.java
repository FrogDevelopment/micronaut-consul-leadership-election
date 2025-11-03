package com.frogdevelopment.micronaut.consul.leadership.client;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;

import io.micronaut.discovery.consul.client.v1.ConsulAslTokenFilter;
import io.micronaut.http.MutableHttpRequest;

@ExtendWith(MockitoExtension.class)
class ConsulLeadershipAuthFilterTest {


    @InjectMocks
    private ConsulLeadershipAuthFilter authFilter;

    @Mock
    private LeadershipConfiguration configuration;

    @Mock
    private MutableHttpRequest<?> request;

    @Test
    void should_addTokenToHeader_when_present() {
        // given
        given(configuration.getToken()).willReturn(Optional.of("my-token"));

        // when
        authFilter.filterRequest(request);

        // then
        then(request).should().header(ConsulAslTokenFilter.HEADER_CONSUL_TOKEN, "my-token");
    }

    @Test
    void should_notAddTokenToHeader_when_absent() {
        // given
        given(configuration.getToken()).willReturn(Optional.empty());

        // when
        authFilter.filterRequest(request);

        // then
        then(request).shouldHaveNoInteractions();
    }
}
