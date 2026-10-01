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

## Answer feedback

Private `ANSWER_REJECTED` events show neutral wrong-answer, already-found,
cooldown, or command-state messages beneath the input. Cooldown uses the server's
`retryAt` to disable submission while keeping the input editable. Stale-version
and invalid-state responses request a current snapshot.

Private `SECONDARY_MATCH` events identify artist, composer, or album discoveries
and explicitly invite another title guess. They never award points, stop playback,
or reveal the title. Discovery categories survive snapshot recovery; their values
are shown only when supplied by a private match event. Round changes clear feedback
and discoveries, and feedback arriving outside play is ignored.

Submitting clears the sent text and returns focus to the input. A delayed response
does not erase a new guess already being typed. Feedback uses a polite live region
and the existing answer-card visual design. Angular tests cover state and timing;
Playwright drives keyboard guesses through mocked STOMP frames, including secondary
discoveries, a repeated discovery, cooldown, and the subsequent title reveal.

## Reveal and round transition

`ROUND_REVEALED` shows the current title, available artist/composer/album metadata,
outcome, awarded points, and total score. It replaces the masked track card and
answer form, keeps the excerpt playing, and announces the result through the polite
live region.
Missing optional metadata is omitted; no year is invented. The stored cover
replaces the reveal card's placeholder; only same-origin `/api/covers/` paths are
rendered, and the placeholder stays when a track has none. Available
streaming links appear as "Listen on" brand-logo links (with accessible names) that open in a new tab; only HTTP(S)
links are rendered.

The countdown projects the event's `transitionAt`, or the recovered snapshot's
`advanceAt`, under [ADR-0015](../adr/0015-four-second-automatic-reveal-transition.md).
Reconnecting restores the reveal metadata, outcome, points, and remaining time.
At zero the UI waits for the server; it never starts or reveals another track itself.
The connected owner can send `ADVANCE_ROUND` with “Next now” or the Enter
key (ignored on auto-repeat or when a button, link, or field has focus); repeated
clicks are blocked while pending. Rejected commands request a snapshot, and recoverable errors
permit retry. The last round instead shows a results countdown and “Finish now”.
Only `GAME_FINISHED` or a finished snapshot opens the existing finish view.

Preparing/started events and snapshots clear the previous reveal. Angular tests
cover each outcome, deadline expiry, final-round recovery, and command guards;
the mocked STOMP browser journey checks title visibility and server-driven advance.
The wire contract is unchanged.

## Audio ownership

`AudioPlaybackService` owns one `HTMLAudioElement` behind an injectable factory.
For a playing round it:

1. stops previous playback and loads the signed URL;
2. waits until the server start instant when necessary;
3. seeks to the excerpt offset plus elapsed time since that instant (modulo the
   looped window), then fades the
   volume in over 500 ms once playback starts;
4. reports blocked autoplay and offers a user-gesture resume action;
5. loops the excerpt window back to its offset when it is shorter than the round,
   both on time updates and when the media ends inside the window;
6. keeps playing past the deadline, so the reveal keeps the music until the next
   round's playback replaces it.

Already expired rounds never start audio. The game room stops playback on finish,
fatal error, snapshot resynchronization, and component destruction. Unit tests use a fake audio
element; a separate browser test checks HTTP byte-range seeking.

## Persisted end summary

`EndScreenComponent` at `/end-screen/{sessionId}` fetches
`GET /sessions/{sessionId}/summary` on its own, independent of any retained socket
or `sessionStorage` state. It renders total score, aggregate outcome counts, and
one row per round with its title, artist, outcome, response time (revealed minus
started), and points, then offers a replay action back to configuration. Because
it re-fetches on every load, the page is refresh-safe: reloading or navigating to
it directly reproduces the same result. A 404 (unowned or unknown session) and
other failures render distinct messages with a way back to configuration. The
game room's own in-session finish view is unchanged and links to this page as
"View summary".

## Current limits

Browser tests cover connection, routing, and mocked answer feedback, not complete
full-stack gameplay. See [status](../product/status.md).

## Source and contract

- [Configuration component](../../frontend/src/app/features/lobby/create-session/create-session.component.ts)
- [GameSessionService](../../frontend/src/app/core/services/game-session.service.ts)
- [End-screen component](../../frontend/src/app/features/end-screen/end-screen.component.ts)
- [GameSocketService](../../frontend/src/app/core/services/game-socket.service.ts)
- [AudioPlaybackService](../../frontend/src/app/core/services/audio-playback.service.ts)
- [Game room](../../frontend/src/app/features/game/game-room/game-room.component.ts)
- [Protocol models](../../frontend/src/app/core/models/game-protocol.model.ts)
- [STOMP contract](../websocket-protocol.md): exact wire payloads and recovery rules.
