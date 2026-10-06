package com.blindtest.model.dto;

/** A guest's chosen nickname and the Turnstile token proving a browser solved the challenge. */
public record GuestJoinRequest(String nickname, String turnstileToken) {
}
