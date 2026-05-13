package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

class TurnstileServiceTest {

    private static final String SECRET = "test-secret";
    private static final String VALID_TOKEN = "valid-cf-token";

    private TurnstileService turnstileService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        turnstileService = new TurnstileService(SECRET, builder.build());
    }

    @Test
    void verifyReturnsTrueWhenCloudflareRespondsSuccess() {
        ResponseActions ra = mockServer.expect(requestTo(TurnstileService.SITEVERIFY_URL));
        ra.andExpect(method(HttpMethod.POST));
        ra.andExpect(content().string(containsString("response=" + VALID_TOKEN)));
        ra.andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertThat(turnstileService.verify(VALID_TOKEN)).isTrue();
        mockServer.verify();
    }

    @Test
    void verifyReturnsFalseWhenCloudflareRespondsFailure() {
        ResponseActions ra = mockServer.expect(requestTo(TurnstileService.SITEVERIFY_URL));
        ra.andExpect(method(HttpMethod.POST));
        ra.andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));

        assertThat(turnstileService.verify("bad-token")).isFalse();
        mockServer.verify();
    }

    @Test
    void verifyReturnsFalseOnNetworkError() {
        ResponseActions ra = mockServer.expect(requestTo(TurnstileService.SITEVERIFY_URL));
        ra.andExpect(method(HttpMethod.POST));
        ra.andRespond(withServerError());

        assertThat(turnstileService.verify(VALID_TOKEN)).isFalse();
        mockServer.verify();
    }

    @Test
    void verifyIncludesSecretInRequest() {
        ResponseActions ra = mockServer.expect(requestTo(TurnstileService.SITEVERIFY_URL));
        ra.andExpect(content().string(containsString("secret=" + SECRET)));
        ra.andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        turnstileService.verify(VALID_TOKEN);
        mockServer.verify();
    }
}
