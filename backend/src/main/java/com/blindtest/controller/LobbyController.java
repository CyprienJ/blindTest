package com.blindtest.controller;

import com.blindtest.model.dto.CreateLobbyRequest;
import com.blindtest.model.dto.GuestJoinRequest;
import com.blindtest.model.dto.GuestJoinResponse;
import com.blindtest.model.dto.LobbyAudioModeRequest;
import com.blindtest.model.dto.LobbyReadyRequest;
import com.blindtest.model.dto.LobbyResponse;
import com.blindtest.model.dto.SoloSessionResponse;
import com.blindtest.service.LobbyService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST boundary for multiplayer lobbies addressed by their join code. The caller UUID is an
 * account ID or, for guest tokens, the guest's participant ID (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/lobbies")
@RequiredArgsConstructor
public class LobbyController {

    private static final int MAXIMUM_ADDRESS_LENGTH = 64;

    private final LobbyService lobbyService;

    /** Opens a lobby hosted by the caller with a validated game configuration. */
    @PostMapping
    public ResponseEntity<LobbyResponse> create(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody(required = false) CreateLobbyRequest request) {
        LobbyResponse response = lobbyService.create(accountId, request);
        return ResponseEntity.created(URI.create("/api/lobbies/" + response.code())).body(response);
    }

    /**
     * Admits a guest with a nickname and Turnstile token (ADR-0020); no account is needed. The
     * returned guest token authorizes only this lobby, its game, and the game's summary.
     */
    @PostMapping("/{code}/guests")
    public ResponseEntity<GuestJoinResponse> joinAsGuest(
            @PathVariable String code,
            @RequestBody(required = false) GuestJoinRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).
                body(lobbyService.joinAsGuest(code, request, clientAddress(servletRequest)));
    }

    /** Returns the lobby to one of its members. */
    @GetMapping("/{code}")
    public ResponseEntity<LobbyResponse> get(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable String code) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).
                body(lobbyService.get(callerId, code));
    }

    /** Joins the lobby; repeating the call returns the current membership. */
    @PostMapping("/{code}/participants")
    public LobbyResponse join(
            @AuthenticationPrincipal UUID accountId,
            @PathVariable String code) {
        return lobbyService.join(accountId, code);
    }

    /** Leaves the lobby. */
    @DeleteMapping("/{code}/participants/me")
    public ResponseEntity<Void> leave(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable String code) {
        lobbyService.leave(callerId, code);
        return ResponseEntity.noContent().build();
    }

    /** Sets the caller's ready flag. */
    @PutMapping("/{code}/participants/me/ready")
    public LobbyResponse setReady(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable String code,
            @RequestBody LobbyReadyRequest request) {
        return lobbyService.setReady(callerId, code, request.ready());
    }

    /** Lets the host choose which devices play the round audio. */
    @PutMapping("/{code}/audio-mode")
    public LobbyResponse setAudioMode(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable String code,
            @RequestBody LobbyAudioModeRequest request) {
        return lobbyService.setAudioMode(callerId, code, request.audioMode());
    }

    /**
     * Lets the host change the playlist, rules, and audio mode; changed rules clear every ready
     * flag.
     */
    @PutMapping("/{code}/settings")
    public LobbyResponse updateSettings(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable String code,
            @RequestBody(required = false) CreateLobbyRequest request) {
        return lobbyService.updateSettings(callerId, code, request);
    }

    /** Lets the host remove another member. */
    @DeleteMapping("/{code}/participants/{participantId}")
    public ResponseEntity<Void> kick(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable String code,
            @PathVariable UUID participantId) {
        lobbyService.kick(callerId, code, participantId);
        return ResponseEntity.noContent().build();
    }

    /** Lets the host start the game; the returned session uses the lobby's session ID. */
    @PostMapping("/{code}/start")
    public SoloSessionResponse start(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable String code) {
        return lobbyService.start(callerId, code);
    }

    /**
     * Best-effort client address for guest join limits: the first forwarded hop when the request
     * came through the proxies, which a client can spoof, so Turnstile and the per-code limit
     * remain the binding controls.
     */
    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",", 2)[0].strip();
            if (!first.isEmpty() && first.length() <= MAXIMUM_ADDRESS_LENGTH) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
