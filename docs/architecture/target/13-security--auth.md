# 13. Security & Auth

[Up: Target design](../target.md)

Historical target design, not an implementation reference. Validated roadmap
decisions and executable code take precedence. See [current architecture](../../architecture.md).

### Registration Flow
```
1. POST /api/auth/register { email, username, password, turnstileToken }
2. Turnstile token verification against the Cloudflare API (siteverify)
3. Account created (emailVerified = false)
4. Email sent with signed token (TTL 24h)
5. POST /api/auth/verify-email { token } → emailVerified = true
```

### Cloudflare Turnstile (anti-bot)
The Turnstile widget is displayed on the registration form. The frontend obtains a `turnstileToken` client-side, which is sent in the `POST /auth/register` request. The backend calls `https://challenges.cloudflare.com/turnstile/v0/siteverify` with the secret key and token before any account creation. A `TurnstileService` encapsulates this HTTP call. The secret key is injected via the `CLOUDFLARE_TURNSTILE_SECRET_KEY` environment variable.

### JWT
- **Access token**: TTL 15 min
- **Refresh token**: TTL 7 days (HttpOnly cookie)
- **Session token** (guest): TTL = session lifetime

### Answer Validation (anti-cheat)
Comparison remains **server-side only**. The delivered pure matcher applies the
normalization and deterministic tiered policy documented under
[Delivered backend](../../backend/README.md#delivered-http-surface), including aliases, token boundaries, and
length-sensitive Damerau–Levenshtein thresholds. It never returns answer text for a
failed guess.

### WebSocket Authorization
The `JwtAuthFilter` validates the token in the STOMP `CONNECT` header. Each `@MessageMapping` verifies that the `Principal` belongs to the target session.

---

*v2.0 — 2026-05-13*
