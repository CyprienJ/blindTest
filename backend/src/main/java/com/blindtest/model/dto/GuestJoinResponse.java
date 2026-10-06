package com.blindtest.model.dto;

/**
 * Guest participant token (ADR-0020) and the lobby it joined. The token authorizes only this
 * lobby, its game, and the game's summary.
 */
public record GuestJoinResponse(String guestToken, LobbyResponse lobby) {
}
