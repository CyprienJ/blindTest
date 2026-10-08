package com.blindtest.model.dto;

/**
 * The lobby a player returns to after a multiplayer game (ADR-0023). Guests get a new token
 * scoped to the reopened lobby's session; registered players get {@code null}.
 */
public record RematchResponse(String guestToken, LobbyResponse lobby) {
}
