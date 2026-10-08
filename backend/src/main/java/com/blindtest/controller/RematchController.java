package com.blindtest.controller;

import com.blindtest.model.dto.RematchResponse;
import com.blindtest.service.LobbyService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns players of a finished multiplayer game to its lobby (ADR-0023). The caller UUID is an
 * account ID or, for guest tokens, the guest's participant ID.
 */
@RestController
@RequiredArgsConstructor
public class RematchController {

    private final LobbyService lobbyService;

    /** Reopens the game's lobby, or joins it when another player already reopened it. */
    @PostMapping("/api/sessions/{sessionId}/rematch")
    public ResponseEntity<RematchResponse> rematch(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).
                body(lobbyService.rematch(callerId, sessionId));
    }
}
