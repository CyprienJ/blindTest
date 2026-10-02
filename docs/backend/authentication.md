# Authentication and authorization

[Up: Backend](README.md)

## Account lifecycle

Registration is invitation-only ([ADR-0017](../adr/0017-invitation-only-registration.md)).
An administrator invites an email address through `/api/admin/invitations`; Resend
delivers a link to `/auth/register?invite=<token>`. The token is 256 random bits,
stored only as a SHA-256 hash, valid for 48 hours, and usable once. Inviting the
same address again revokes the previous link, and administrators can resend or
revoke an invitation. Addresses that already have an account cannot be invited.

The registration page reads the invited address from
`POST /api/auth/invitations/lookup`. `POST /api/auth/register` takes the
invitation token, a username, a password, and a Cloudflare Turnstile token; the
email always comes from the invitation. A single conditional update consumes the
invitation, so concurrent requests cannot reuse it, and any failure rolls back
both the account and the consumption. Accounts are created as verified `USER`
accounts, receive a welcome email, and the browser signs in at once. Bounded
server-side Turnstile verification rejects invalid challenges with safe
diagnostics. Resend also sends password-reset messages, and verification messages
for accounts that predate invitations, through an injectable email boundary.
Login issues a JWT access token and an opaque refresh token.

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
valid token. Browser WebSocket handshakes must also originate from the configured
CORS origin. Private events use principal-scoped queues. Read the
[STOMP contract](../websocket-protocol.md) for the exact authorization protocol.

## Configuration and operations

Development, test, and production profiles provide separate settings. Production
configuration validates secrets and endpoints at startup. Do not put credentials,
tokens, signed media URLs, or raw guesses into logs. Request correlation IDs support
HTTP diagnostics without exposing secrets.

The first administrator registers through an invitation printed by the
`invite-first-admin` operator command. A second one-shot command then promotes
that account, with locking, replay safety, and audit persistence. There is no
bootstrap web endpoint. See [provisioning](../admin-provisioning.md).

## Source entry points

- [AuthController](../../backend/src/main/java/com/blindtest/controller/AuthController.java),
  [AuthService](../../backend/src/main/java/com/blindtest/service/AuthService.java),
  and [RegistrationService](../../backend/src/main/java/com/blindtest/service/RegistrationService.java)
- [AdminInvitationController](../../backend/src/main/java/com/blindtest/controller/admin/AdminInvitationController.java)
  and [InvitationService](../../backend/src/main/java/com/blindtest/service/InvitationService.java)
- [SecurityConfig](../../backend/src/main/java/com/blindtest/config/SecurityConfig.java)
  and [security package](../../backend/src/main/java/com/blindtest/security)
- [WebSocket authorization](../../backend/src/main/java/com/blindtest/websocket)
- [Frontend auth service](../../frontend/src/app/core/services/auth.service.ts)
- [Configuration guide](../development/configuration.md)
