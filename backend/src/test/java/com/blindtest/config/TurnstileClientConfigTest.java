package com.blindtest.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;

class TurnstileClientConfigTest {

    @Test
    void requestFactoryUsesBoundedConnectionAndReadTimeouts() {
        TurnstileClientConfig config = new TurnstileClientConfig();

        SimpleClientHttpRequestFactory requestFactory = config.requestFactory();

        assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout")).
                isEqualTo(Math.toIntExact(TurnstileClientConfig.CONNECT_TIMEOUT.toMillis()));
        assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).
                isEqualTo(Math.toIntExact(TurnstileClientConfig.READ_TIMEOUT.toMillis()));
    }

    @Test
    void exposesTheDedicatedRestClient() {
        TurnstileClientConfig config = new TurnstileClientConfig();

        assertThat(config.turnstileRestClient()).isNotNull();
    }
}
