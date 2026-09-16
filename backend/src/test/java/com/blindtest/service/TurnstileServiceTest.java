package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class TurnstileServiceTest {

    private static final String SECRET = "test-secret-never-log";
    private static final String VALID_TOKEN = "valid-cf-token-never-log";

    private TurnstileService turnstileService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        turnstileService = new TurnstileService(SECRET, builder.build());
    }

    @Test
    void verifySendsAFormRequestAndReturnsTrueForACompleteSuccessfulResponse(
            CapturedOutput output) {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("secret", SECRET);
        expectedForm.add("response", VALID_TOKEN);

        ResponseActions response = mockServer.expect(requestTo(TurnstileService.SITEVERIFY_URL));
        response.andExpect(method(HttpMethod.POST));
        response.andExpect(
                content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED));
        response.andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE));
        response.andExpect(content().formData(expectedForm));
        response.andRespond(
                withSuccess(
                        """
                        {
                          "success": true,
                          "challenge_ts": "2026-07-31T07:29:16.123Z",
                          "hostname": "localhost",
                          "action": "register",
                          "error-codes": [],
                          "cdata": "ignored"
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        assertThat(turnstileService.verify(VALID_TOKEN)).isTrue();
        assertThat(output).doesNotContain(SECRET, VALID_TOKEN);
        mockServer.verify();
    }

    @Test
    void verifyLogsSafeCloudflareDetailsAndReturnsFalseForARejectedToken(
            CapturedOutput output) {
        String rejectedToken = "rejected-token-never-log";
        ResponseActions response = mockServer.expect(requestTo(TurnstileService.SITEVERIFY_URL));
        response.andRespond(
                withSuccess(
                        """
                        {
                          "success": false,
                          "challenge_ts": "2026-07-31T07:29:16Z",
                          "hostname": "localhost",
                          "action": "register",
                          "error-codes": [
                            "invalid-input-response",
                            "timeout-or-duplicate"
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        assertThat(turnstileService.verify(rejectedToken)).isFalse();
        assertThat(output).
                contains("Turnstile siteverify rejected a token").
                contains("invalid-input-response").
                contains("timeout-or-duplicate").
                contains("hostname=localhost").
                contains("action=register").
                contains("challengeTimestamp=2026-07-31T07:29:16Z").
                doesNotContain(SECRET, rejectedToken);
        mockServer.verify();
    }

    @Test
    void verifyReturnsFalseAndLogsAnEmptyResponseWithoutSensitiveData(
            CapturedOutput output) {
        mockServer.expect(requestTo(TurnstileService.SITEVERIFY_URL)).
                andRespond(withNoContent());

        assertThat(turnstileService.verify(VALID_TOKEN)).isFalse();
        assertThat(output).
                contains("Turnstile siteverify returned an empty response").
                doesNotContain(SECRET, VALID_TOKEN);
        mockServer.verify();
    }

    @Test
    void verifyReturnsFalseAndLogsOnlyTheExceptionTypeOnHttpFailure(
            CapturedOutput output) {
        mockServer.expect(requestTo(TurnstileService.SITEVERIFY_URL)).
                andRespond(
                        withServerError().
                                body(SECRET + " " + VALID_TOKEN).
                                contentType(MediaType.TEXT_PLAIN));

        assertThat(turnstileService.verify(VALID_TOKEN)).isFalse();
        assertThat(output).
                contains("Turnstile siteverify request failed: exceptionType=").
                doesNotContain(SECRET, VALID_TOKEN);
        mockServer.verify();
    }

    @Test
    void verifyFailsClosedWhenCloudflareReturnsMalformedJson(CapturedOutput output) {
        mockServer.expect(requestTo(TurnstileService.SITEVERIFY_URL)).
                andRespond(withSuccess("{not-json}", MediaType.APPLICATION_JSON));

        assertThat(turnstileService.verify(VALID_TOKEN)).isFalse();
        assertThat(output).
                contains("Turnstile siteverify request failed: exceptionType=").
                doesNotContain(SECRET, VALID_TOKEN);
        mockServer.verify();
    }

    @Test
    void siteverifyResponseMapsCloudflareFieldNames() {
        TurnstileService.SiteverifyResponse response =
                new TurnstileService.SiteverifyResponse(
                        false,
                        List.of("invalid-input-secret"),
                        "example.com",
                        "register",
                        Instant.parse("2026-07-31T07:29:16Z"));

        assertThat(response.errorCodes()).containsExactly("invalid-input-secret");
        assertThat(response.hostname()).isEqualTo("example.com");
        assertThat(response.action()).isEqualTo("register");
        assertThat(response.challengeTimestamp()).
                isEqualTo(Instant.parse("2026-07-31T07:29:16Z"));
    }
}
