# ADR-0020: Let guests join multiplayer games with a code

- Status: Accepted
- Date: 2026-10-02
- Deciders: Product owner and engineering
- Related tickets: MP-08, MP-09
- Supersedes: N/A
- Superseded by: N/A

## Context

Registration is invitation-only ([ADR-0017](0017-invitation-only-registration.md)).
The reference join screen says "You're joining as a guest — no account
required." On 2026-10-02 the product owner decided that anyone holding a live join
code may play as a guest. Account creation stays invitation-only.

## Decision

- `POST /api/lobbies/{code}/guests` accepts a nickname and a Turnstile token and
  returns a guest participant token scoped to that one session;
- the token is a short-lived signed JWT with a distinct `guest` audience; it
  carries the session and participant IDs and no account ID; its expiry equals
  the session inactivity window, and the server also rejects it as soon as its
  session is no longer live or the guest was kicked;
- guest tokens authorize only that session's STOMP connection, subscriptions,
  commands, and in-session summary; every REST endpoint that needs an account
  rejects them;
- nicknames are 2–20 characters after trimming, must be unique in the lobby
  regardless of case, and are rendered as text only;
- the token is kept in browser `sessionStorage`, so a tab reload can rejoin while
  a closed tab cannot;
- guests have no durable history: the completed game stores them as `GUEST`
  participants with nickname, score, and rank only, visible to registered
  participants of that game;
- guest joins are rate-limited per IP and per code, and a failed code lookup
  does not reveal whether a session exists.

## Consequences

- Groups can play without first inviting every friend.
- Invitation-only registration still limits who owns durable accounts and data.
- A guest endpoint is a new public attack surface; Turnstile, rate limits, the
  10-participant cap, and short code validity bound it.
- Guests cannot recover a game after closing the tab.

## Alternatives Considered

- **Accounts only:** simpler, but every player would need an invitation first.
- **Guest tokens in `localStorage`:** survive a closed tab but leak more easily
  across sessions on shared machines.

## Implementation notes (2026-10-06)

Delivered with MP-08 and MP-09. Two details differ from the wording above:

- the token lives for a fixed six hours (`jwt.guest-token.ttl-ms`) rather than the
  30-minute inactivity window, because a guest could not reconnect or read the
  summary once a long game outlived it; liveness is still enforced on every request
  by checking that the guest is a member of a live lobby or session;
- the per-client limit reads the first `X-Forwarded-For` hop, which a client can
  forge. Turnstile and the per-code limit remain the binding controls, and the
  per-client limit (30 attempts per 10 minutes) stays above a full lobby because
  players in one room share an address.

## Follow-up

- Add guest token verification next to the account JWT path in MP-08.
- Extend the security verification matrix with guest isolation cases in MP-10.

## References

- [ADR-0019](0019-multiplayer-session-rules.md)
- [Authentication](../backend/authentication.md)
