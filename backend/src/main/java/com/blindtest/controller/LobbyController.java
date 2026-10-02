package com.blindtest.controller;

import com.blindtest.model.dto.CreateSoloSessionRequest;
import com.blindtest.model.dto.LobbyReadyRequest;
import com.blindtest.model.dto.LobbyResponse;
import com.blindtest.model.dto.SoloSessionResponse;
import com.blindtest.service.LobbyService;
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

/** Authenticated REST boundary for multiplayer lobbies addressed by their join code. */
@RestController
@RequestMapping("/api/lobbies")
@RequiredArgsConstructor
public class LobbyController {

    private final LobbyService lobbyService;

    /** Opens a lobby hosted by the caller with a validated game configuration. */
    @PostMapping
    public ResponseEntity<LobbyResponse> create(
            @AuthenticationPrincipal UUID accountId,
            @RequestBody(required = false) CreateSoloSessionRequest request) {
        LobbyResponse response = lobbyService.create(accountId, request);
        return ResponseEntity.created(URI.create("/api/lobbies/" + response.code())).body(response);
    }

    /** Returns the lobby to one of its members. */
    @GetMapping("/{code}")
    public ResponseEntity<LobbyResponse> get(
            @AuthenticationPrincipal UUID accountId,
            @PathVariable String code) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).
                body(lobbyService.get(accountId, code));
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
            @AuthenticationPrincipal UUID accountId,
            @PathVariable String code) {
        lobbyService.leave(accountId, code);
        return ResponseEntity.noContent().build();
    }

    /** Sets the caller's ready flag. */
    @PutMapping("/{code}/participants/me/ready")
    public LobbyResponse setReady(
            @AuthenticationPrincipal UUID accountId,
            @PathVariable String code,
            @RequestBody LobbyReadyRequest request) {
        return lobbyService.setReady(accountId, code, request.ready());
    }

    /** Lets the host remove another member. */
    @DeleteMapping("/{code}/participants/{participantId}")
    public ResponseEntity<Void> kick(
            @AuthenticationPrincipal UUID accountId,
            @PathVariable String code,
            @PathVariable UUID participantId) {
        lobbyService.kick(accountId, code, participantId);
        return ResponseEntity.noContent().build();
    }

    /** Lets the host start the game; the returned session uses the lobby's session ID. */
    @PostMapping("/{code}/start")
    public SoloSessionResponse start(
            @AuthenticationPrincipal UUID accountId,
            @PathVariable String code) {
        return lobbyService.start(accountId, code);
    }
}
