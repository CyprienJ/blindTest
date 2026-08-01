package com.blindtest.service;

import com.blindtest.config.properties.TurnstileProperties;
import com.blindtest.service.port.TurnstileVerifier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Validates Cloudflare Turnstile tokens by calling the siteverify API. */
@Service
public class TurnstileService implements TurnstileVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(TurnstileService.class);

    static final String SITEVERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secretKey;
    private final RestClient restClient;

    /** Constructs the service with its secret key and dedicated HTTP client. */
    @Autowired
    public TurnstileService(
            TurnstileProperties properties,
            @Qualifier("turnstileRestClient") RestClient restClient) {
        this(properties.secretKey(), restClient);
    }

    /** Package-private constructor used in tests to inject a custom {@link RestClient}. */
    TurnstileService(String secretKey, RestClient restClient) {
        this.secretKey = secretKey;
        this.restClient = restClient;
    }

    /**
     * Returns {@code true} if the given Turnstile token is valid according to Cloudflare.
     * Returns {@code false} on any API failure or invalid token.
     */
    @Override
    public boolean verify(String token) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", secretKey);
            form.add("response", token);
            var spec = restClient.post().
                    uri(SITEVERIFY_URL).
                    contentType(MediaType.APPLICATION_FORM_URLENCODED).
                    accept(MediaType.APPLICATION_JSON).
                    body(form);
            SiteverifyResponse response = spec.retrieve().body(SiteverifyResponse.class);
            if (response == null) {
                LOG.warn("Turnstile siteverify returned an empty response");
                return false;
            }
            if (!response.success()) {
                LOG.warn(
                        "Turnstile siteverify rejected a token: errorCodes={}, hostname={},"
                                + " action={}, challengeTimestamp={}",
                        response.errorCodes(),
                        response.hostname(),
                        response.action(),
                        response.challengeTimestamp());
                return false;
            }
            return true;
        } catch (Exception e) {
            LOG.warn(
                    "Turnstile siteverify request failed: exceptionType={}",
                    e.getClass().getSimpleName());
            return false;
        }
    }

    /** Cloudflare siteverify response shape. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SiteverifyResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("error-codes") List<String> errorCodes,
            @JsonProperty("hostname") String hostname,
            @JsonProperty("action") String action,
            @JsonProperty("challenge_ts") Instant challengeTimestamp) {
    }
}
