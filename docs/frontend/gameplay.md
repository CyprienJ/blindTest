# Gameplay client and audio

[Up: Frontend](README.md) · [Server sessions](../backend/sessions.md)

## From configuration to the room

The configuration component validates local controls and sends playlist ID,
track count, maximum round seconds, and an empty extensions object to
`POST /api/sessions`. The backend remains authoritative for eligibility and limits.
On success, the browser stores the active session and navigates to
`/game/{sessionId}`. Error states map backend failures into actionable messages.

`GameSessionService` retains the session in memory and `sessionStorage` so a reload
can reconnect. It tracks one current browser session even though the backend
allows an account to create multiple sessions. Opening an ID that is not locally
retained returns to configuration; it does not fetch arbitrary session details.

## Connection and room state

`GameSocketService` connects with the access JWT and subscribes to shared session
and private user destinations. It emits typed events and tracks the authoritative
session version used in commands. Reconnection requests a snapshot; disconnect
and logout tear down subscriptions.

The game room requests its initial snapshot and starts a `CREATED` game. It
renders connecting, starting, preparing, playing, revealed, finished, and error
states. Public events drive round numbers, deadlines, outcome, and score. Answer
and skip actions send commands; client button guards supplement server rate limits.

The browser countdown is presentation based on server timestamps and `Date.now()`.
The backend decides whether an answer arrived in time and how many points it earns.
The browser cannot change the authoritative deadline or score.

## Audio ownership

`AudioPlaybackService` owns one `HTMLAudioElement` behind an injectable factory.
For a playing round it:

1. stops previous playback and loads the signed URL;
2. waits until the server start instant when necessary;
3. seeks to the excerpt offset plus elapsed time since that instant;
4. reports blocked autoplay and offers a user-gesture resume action;
5. stops at the deadline with a 300 ms cleanup grace, or on round teardown.

Already expired rounds never start audio. The game room also stops playback on
reveal, finish, fatal error, and component destruction. Unit tests use a fake audio
element; a separate browser test checks HTTP byte-range seeking.

## Current limits

Secondary discoveries and rejected guesses currently clear submission state without
the full planned feedback UI. A basic finished view consumes the live finish event;
the separate end-screen component is still a placeholder and does not load the
durable REST summary. Existing browser game-room tests cover connection and routing,
not complete full-stack gameplay. See [status](../product/status.md).

## Source and contract

- [Configuration component](../../frontend/src/app/features/lobby/create-session/create-session.component.ts)
- [GameSessionService](../../frontend/src/app/core/services/game-session.service.ts)
- [GameSocketService](../../frontend/src/app/core/services/game-socket.service.ts)
- [AudioPlaybackService](../../frontend/src/app/core/services/audio-playback.service.ts)
- [Game room](../../frontend/src/app/features/game/game-room/game-room.component.ts)
- [Protocol models](../../frontend/src/app/core/models/game-protocol.model.ts)
- [STOMP contract](../websocket-protocol.md): exact wire payloads and recovery rules.
