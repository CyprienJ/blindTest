# Authentication and authorization

[Up: Backend](README.md)

## Account lifecycle

Registration validates user input and a Cloudflare Turnstile challenge. Bounded
server-side verification rejects invalid challenges with safe diagnostics. Resend
sends email verification and password-reset messages through an injectable email
boundary. Login issues a JWT access token and an opaque refresh token.

Refresh tokens are stored in PostgreSQL and rotated on refresh. They travel in
Secure, HttpOnly, SameSite=Strict cookies scoped to `/api/auth`. Access JWTs are
sent in the HTTP authorization header; the frontend stores them in `localStorage`.
Logout invalidates refresh state, and the browser clears local authentication even
when server logout fails.

Password recovery and reset exist, but reset JWTs are not persisted as single-use
tokens. See [known limitations](../product/status.md#known-release-risks).

## Authorization boundaries

Public catalogue reads and authentication endpoints are accessible anonymously.
Administrator HTTP endpoints require the `ADMIN` role. Solo creation additionally
requires a verified account, and completed summaries are restricted to their owner.
Frontend guards guide navigation; backend checks remain authoritative.

STOMP `CONNECT` uses an access JWT in its native `Authorization` header. Subsequent
game commands and subscriptions require active membership and exact destinations.
A connected session can survive access-token expiry, but reconnecting requires a
valid token. Private events use principal-scoped queues. Read the
[STOMP contract](../websocket-protocol.md) for the exact authorization protocol.

## Configuration and operations

Development, test, and production profiles provide separate settings. Production
configuration validates secrets and endpoints at startup. Do not put credentials,
tokens, signed media URLs, or raw guesses into logs. Request correlation IDs support
HTTP diagnostics without exposing secrets.

The first administrator is promoted from an existing verified account by a
one-shot operator command, with locking, replay safety, and audit persistence.
There is no bootstrap web endpoint. See [provisioning](../admin-provisioning.md).

## Source entry points

- [AuthController](../../backend/src/main/java/com/blindtest/controller/AuthController.java)
  and [AuthService](../../backend/src/main/java/com/blindtest/service/AuthService.java)
- [SecurityConfig](../../backend/src/main/java/com/blindtest/config/SecurityConfig.java)
  and [security package](../../backend/src/main/java/com/blindtest/security)
- [WebSocket authorization](../../backend/src/main/java/com/blindtest/websocket)
- [Frontend auth service](../../frontend/src/app/core/services/auth.service.ts)
- [Configuration guide](../development/configuration.md)
