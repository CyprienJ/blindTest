package com.blindtest.service;

import com.blindtest.config.properties.TurnstileProperties;
import com.blindtest.service.port.TurnstileVerifier;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Validates Cloudflare Turnstile tokens by calling the siteverify API. */
@Service
public class TurnstileService implements TurnstileVerifier {

    static final String SITEVERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secretKey;
    private final RestClient restClient;

    /** Constructs the service with the Turnstile secret key and a default {@link RestClient}. */
    @Autowired
    public TurnstileService(TurnstileProperties properties) {
        this.secretKey = properties.secretKey();
        this.restClient = RestClient.create();
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
            var spec = restClient.post().uri(SITEVERIFY_URL).body(form);
            SiteverifyResponse response = spec.retrieve().body(SiteverifyResponse.class);
            return response != null && response.success();
        } catch (Exception e) {
            return false;
        }
    }

    /** Cloudflare siteverify response shape. */
    record SiteverifyResponse(@JsonProperty("success") boolean success) {
    }
}
