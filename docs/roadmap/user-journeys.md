# 3. First-Release User Journeys

[Up: Roadmap](../plan.md)

### Visitor and account

1. A visitor reaches a public home page with clear login and registration actions.
2. The visitor registers with username, email, password, and Turnstile.
3. The user receives an email and verifies the account.
4. The user logs in.
5. The user can recover a forgotten password.
6. The authenticated user reaches a role-aware home page and can continue to game
   creation or joining.

### Administrator catalogue

1. An administrator submits a supported YouTube URL and metadata.
2. The backend downloads and converts the media.
3. The finalized MP3 is uploaded to MinIO.
4. Import progress is visible to the initiating administrator.
5. The administrator previews the imported audio.
6. The administrator corrects metadata and alternative answers.
7. The administrator publishes or rejects the track.

### Administrator playlist

1. An administrator creates a public playlist.
2. Published tracks are searched and added.
3. Tracks can be removed and reordered.
4. The public playlist detail preserves that order.

### Solo game

1. An authenticated user selects a playlist.
2. The user selects the track count and per-track time limit.
3. The backend creates a one-player game session and snapshots the selection.
4. The server starts a round and sends its start time and a temporary audio URL.
5. The browser starts playback and displays the countdown.
6. The user submits guesses:
   - secondary metadata is acknowledged without score;
   - a correct title ends the round and awards time-based points.
7. The user may skip; otherwise the timer may expire.
8. The server reveals the answer and advances to the next round.
9. The final result and round detail are persisted.
10. The user sees the end summary.
