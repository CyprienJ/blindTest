package com.blindtest.controller;

import com.blindtest.model.dto.CreateSoloSessionRequest;
import com.blindtest.model.dto.SoloSessionResponse;
import com.blindtest.service.SoloSessionService;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated REST boundary for creating server-authoritative game sessions. */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SoloSessionService soloSessionService;

    /** Creates a one-player session and returns its STOMP connection instructions. */
    @PostMapping
    public ResponseEntity<SoloSessionResponse> createSoloSession(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody(required = false) CreateSoloSessionRequest request) {
        SoloSessionResponse response = soloSessionService.create(accountId, request);
        return ResponseEntity.created(URI.create("/api/sessions/" + response.sessionId())).
                body(response);
    }
}
