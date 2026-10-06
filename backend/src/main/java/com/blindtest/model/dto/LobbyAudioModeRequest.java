package com.blindtest.model.dto;

import com.blindtest.model.lobby.AudioMode;

/** Host request choosing which devices play the round audio. */
public record LobbyAudioModeRequest(AudioMode audioMode) {
}
