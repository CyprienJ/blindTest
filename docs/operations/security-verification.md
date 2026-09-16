# Security verification

[Up: Operations](README.md) · [Testing](../testing.md)

This page is the E2E-07 release evidence register. It records the controls that
are verified in code and the residual findings currently visible in the product
inventory.

## Authorization and enumeration

| Boundary | Required result | Evidence |
|---|---|---|
| Public catalogue reads | Anonymous catalogue, playlist, and session bootstrap reads work | `SecurityConfigIntegrationTest` |
| Authenticated routes | Missing or invalid access tokens return the safe 401 contract | `SecurityConfigIntegrationTest` |
| Administrator routes | Anonymous callers receive 401; users receive 403; admins reach the controller | `SecurityConfigIntegrationTest` |
| Completed summaries | Only the owner can read a summary; another account and unknown IDs both receive 404 | `GameHistorySummaryService`, `SessionSummaryControllerIntegrationTest` |
| Active game destinations | Only members can subscribe or send, with one canonical UUID destination | `SessionWebSocketAuthorizationIntegrationTest`, `SessionDestinationAuthorizationInterceptorTest` |
| Media objects | Storage is private and URLs are short-lived bearer credentials issued after domain checks | `AdminTrackServiceTest`, `SessionGameplayServiceTest`, `MinioMediaStorageIntegrationTest` |

Unknown and unauthorized owner-controlled IDs deliberately return the same 404.
Published catalogue IDs remain enumerable because that content is public;
private object keys and unpublished metadata never cross that boundary.

## Browser and transport controls

REST and WebSocket origins use the configured `cors.allowed-origins` value. The
frontend Nginx configuration emits `nosniff`, frame denial, strict referrer and
permissions policies, and a CSP allowing only the application, Cloudflare
Turnstile, and configured Google Fonts resources. Production TLS termination
must emit HSTS; the plain HTTP container intentionally does not set it.

Refresh tokens are rotated in PostgreSQL and sent as `Secure`, `HttpOnly`,
`SameSite=Strict` cookies scoped to `/api/auth`; access JWTs use the
`Authorization` header. Summaries use `Cache-Control: no-store` and public
catalogue responses contain no signed URLs.

## Bounds and abuse controls

DTO validation bounds account fields, metadata, aliases, playlist operations,
catalogue pagination, and YouTube URLs. Import processing enforces a 25 MiB
media limit and a process timeout. The server rejects oversized request headers
and form bodies before controller work. Gameplay commands have a
20-command-per-session-second burst limit and a 300 ms per-player answer
cooldown; repeated abuse feedback is bounded and later commands are dropped.

## Automated release checks

```bash
git grep -nE '(JWT_SECRET|S3_SECRET_KEY|RESEND_API_KEY|CLOUDFLARE_TURNSTILE_SECRET_KEY)=' -- ':!*.md' ':!backend/src/test/**' || true
cd backend && mvn --batch-mode checkstyle:check test
cd ../frontend && npm ci && npm audit --audit-level=high
```

CI runs Gitleaks over the repository, `npm audit` for frontend dependencies,
and GitHub Dependency Review on pull requests. A high or critical dependency
finding must be upgraded, removed, or recorded here with an owner and release
decision before deployment.

## Accepted residual findings

The access JWT is stored in browser `localStorage`, so an XSS vulnerability
would allow token theft. CSP, Angular escaping, and dependency audits reduce the
risk; a future security-sensitive release should move access-token handling to a
stronger browser boundary.

Password-reset JWTs are valid until expiry and have no persisted one-time-use
marker. This is recorded in [current status](../product/status.md#known-release-risks).
The short lifetime and verified email flow keep this from being a critical/high
release blocker for the current scope. A future release should add a consumed
marker or an opaque persisted reset token.
