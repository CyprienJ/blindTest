# Environment and configuration

[Up: Local development](README.md) · [Deployment](../operations/README.md)

Copy the root [.env.example](../../.env.example) and replace placeholders for the
environment you are running. Docker Compose reads `.env`; a host-launched JVM
needs its configuration made available separately in the shell or run configuration.
Never commit credentials or the populated `.env` file.

## Configuration groups

| Variables | Purpose |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database identity and credentials |
| `JWT_SECRET` | Access/reset token signing; at least 32 ASCII characters |
| `RESEND_API_KEY` | Outbound account email |
| `CLOUDFLARE_TURNSTILE_SECRET_KEY` | Server anti-bot verification |
| `APP_BASE_URL`, `APP_PORT`, `CORS_ALLOWED_ORIGINS` | Browser origin, local entry port and allowed origins |
| `S3_ENDPOINT`, `S3_PUBLIC_ENDPOINT` | Internal storage operations and browser-facing signing endpoint |
| `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` | Private object storage configuration |
| `MINIO_API_PORT`, `MINIO_CONSOLE_PORT` | Local storage service access |
| `APP_IMPORT_TEMP_DIR`, `APP_IMPORT_MAX_SIZE_BYTES`, `APP_IMPORT_TIMEOUT`, `BACKEND_CPU_LIMIT` | Import workspace/resource bounds |
| `MEDIA_DELETION_POLL_DELAY_MS` | Media cleanup polling interval |

Session defaults in `application.properties` are `app.session.inactive-ttl=30m`,
`app.session.finished-ttl=5m`, and `app.session.cleanup-delay=PT1M`.

## Profiles and endpoints

Development is the default Spring profile. Compose automatically applies
`docker-compose.override.yml`, which selects local development behavior and
Cloudflare's matching public test keys. The base Compose file is production-like:
selecting it alone requires valid production secrets and HTTPS endpoints.

The production frontend joins the external Docker network `proxy` with the alias
`nosblindtests`; MinIO joins it with the alias `blindtest-minio` for the separate
browser-facing media proxy. Nginx Proxy Manager uses these aliases as its
application and media targets.
Local Compose overrides replace that external network with a project-local
`blindtest_proxy` network so local startup does not depend on the VPS proxy.

The browser must be able to reach the public S3 endpoint embedded in signed URLs.
An internal Compose hostname works for backend storage calls but is not normally
a browser-reachable media hostname. Keep CORS restricted to the intended browser
origin and preserve query strings through a media reverse proxy.

The complete Nginx Proxy Manager setup is in the
[reverse-proxy runbook](../operations/reverse-proxy.md).

## Source of exact defaults

- [Environment template](../../.env.example)
- [Spring resources/profiles](../../backend/src/main/resources)
- [Typed configuration](../../backend/src/main/java/com/blindtest/config)
- [Base Compose](../../docker-compose.yml) and [local override](../../docker-compose.override.yml)
- [Media limits and recovery](../media-operations.md)
