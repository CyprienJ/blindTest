# 1. Validated Product Decisions

[Up: Roadmap](../plan.md)

### First production release

The first release must provide:

- account registration with verified email;
- login, logout, token refresh, and password recovery;
- an administrator-only global music catalogue;
- administrator-only YouTube imports;
- an administrator preview and validation step before publication;
- administrator-only public playlist creation and editing;
- a solo game for one authenticated player in one browser;
- game configuration:
  - public playlist;
  - number of tracks;
  - maximum answer time per track;
- typed answers with tolerant matching;
- a score based on remaining time;
- skip;
- timeout and answer reveal;
- an end-of-game summary;
- persistence of completed games from the first release, even though the full
  history UI comes later;
- music files stored in private S3-compatible object storage;
- MinIO hosted on the VPS initially.

### Solo game rules

- The player creating a game must be authenticated.
- The expected primary answer is `title`:
  - the film title for a film-related track;
  - the music title otherwise.
- A correct title ends the round immediately.
- A correct artist, composer, or album answer:
  - is acknowledged privately to the player;
  - awards no points;
  - does not end the round;
  - can only be acknowledged once per field;
  - allows the player to continue guessing the title.
- Manually configured alternative answers must be supported.
- Wrong answers do not remove points.
- A short server-enforced anti-spam cooldown may reject overly frequent attempts.
- Timeout reveals the answer and awards zero title points.
- Skip reveals the answer and awards zero title points.
- The scoring model awards up to 1,000 points, decreasing linearly with authoritative
  elapsed server time. Correct titles accepted before the deadline receive at least
  100 points and use positive-half-up integer rounding under ADR-0007.

### Product administration

- Only administrators import tracks.
- Imported tracks join one global catalogue.
- Only administrators create and edit playlists.
- Playlists are public.
- Imported media retains the current preview/validate/reject workflow.

### Technical direction

- The backend remains a modular Spring Boot monolith for the first release.
- PostgreSQL stores durable product and history data.
- MinIO stores music objects through an S3-compatible storage abstraction.
- The bucket is private; the application generates short-lived pre-signed playback
  URLs after authorization.
- The server is authoritative for game state, timing, answer validation, and score.
- Even a solo game is represented as a server-side game session with one player.
- STOMP/WebSocket is used from the first release for game events so multiplayer
  does not require replacing the transport model.
- Active sessions initially live in memory behind a replaceable `SessionStore`
  interface.
- A later implementation may replace the in-memory store/event transport with
  Redis without changing the game domain.
- The existing generated design in `docs/Blindtest/Blindtest Views.html` remains
  the visual reference.
- Desktop is the first-release target. A phone layout must not crash, but mobile
  gameplay is not a release requirement.
