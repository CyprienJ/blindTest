# Existing reverse proxy

[Up: Operations](README.md) · [Production configuration](../development/configuration.md)

The production stack is published through the existing Nginx Proxy Manager
(NPM) installation. NPM and the Blindtest frontend share the external Docker
network `proxy`; the frontend is addressed as `nosblindtests:80`. MinIO also
joins that network under the dedicated alias `blindtest-minio`, but its console
and host bindings remain private.

The two proxy hosts below are additive configuration. Do not edit or recreate
the other site's proxy host, certificate, network membership, or redirect.

## Prepare the shared network

Run this on the VPS before starting either Compose project. The existing NPM
container must be attached to `proxy`; the Blindtest Compose project attaches
the frontend and MinIO automatically.

```bash
docker network inspect proxy >/dev/null \
  || docker network create --driver bridge proxy
docker inspect --format '{{json .NetworkSettings.Networks}}' nginx-proxy-manager
docker network connect proxy nginx-proxy-manager 2>/dev/null || true
docker compose -p blindtest-prod up -d
docker compose -p blindtest-prod ps
```

Replace `nginx-proxy-manager` with the actual NPM container name when it is
different. The `network connect` command is idempotent for this runbook; a
successful existing connection may report an error that can be ignored.

## Application host

Create an NPM **Proxy Host** for the public application hostname, for example
`nosblindtests.fr`:

| Setting | Value |
|---|---|
| Scheme | `http` |
| Forward hostname/IP | `nosblindtests` |
| Forward port | `80` |
| Websockets Support | enabled |
| Block Common Exploits | enabled |
| Cache Assets | disabled |

Request a new Let's Encrypt certificate for the hostname, enable **Force SSL**
and **HTTP/2**, and include only the intended public hostname in the certificate.
The NPM host forwards `/api/` to the frontend container, which forwards it to
the backend; `/ws` and `/ws-sockjs/` use the frontend's WebSocket upgrade
configuration.

In the host's Advanced configuration, use limits appropriate to the small
browser requests and long-lived STOMP connection:

```nginx
client_max_body_size 1m;
proxy_connect_timeout 10s;
proxy_send_timeout 30s;
proxy_read_timeout 86400s;
```

NPM's WebSocket option supplies the HTTP/1.1 upgrade headers. Keep the
application's `APP_BASE_URL` and `CORS_ALLOWED_ORIGINS` equal to the HTTPS
application origin.

## Media host

Create a second NPM **Proxy Host**, preferably `media.nosblindtests.fr`, for the
browser-facing S3 endpoint used to create signed URLs:

| Setting | Value |
|---|---|
| Scheme | `http` |
| Forward hostname/IP | `blindtest-minio` |
| Forward port | `9000` |
| Websockets Support | disabled |
| Block Common Exploits | enabled |
| Cache Assets | disabled |

Issue a separate Let's Encrypt certificate and enable **Force SSL** and
**HTTP/2**. In Advanced configuration, preserve the signed query string and
support HTML audio range requests:

```nginx
client_max_body_size 1m;
proxy_connect_timeout 10s;
proxy_send_timeout 60s;
proxy_read_timeout 60s;
proxy_buffering off;
proxy_force_ranges on;
```

Do not add a `/console` route or expose port `9001`. The MinIO API is reachable
from NPM only through `blindtest-minio:9000`; the Compose host binding remains
`127.0.0.1:${MINIO_API_PORT:-9000}` for diagnostics. The generated signed URL
must use the media hostname (`S3_PUBLIC_ENDPOINT=https://media.nosblindtests.fr`)
and NPM must forward its complete query string unchanged. Do not add a rewrite,
redirect, cache key, or query-parameter allowlist to this host.

## Verification

After DNS points both names to the VPS, verify the proxy path from a machine
outside the VPS:

```bash
curl --fail --silent --show-error https://nosblindtests.fr/actuator/health
curl --fail --silent --show-error -I https://media.nosblindtests.fr/minio/health/live
```

The first response must be the backend health response through
`nosblindtests:80`; the second must be served by MinIO through port `9000`.
Complete a real game to verify a signed media URL in the browser, including
seeking within the track. In browser developer tools, confirm the media request
retains its `X-Amz-*` query parameters, returns `206 Partial Content` for a
range request, and uses HTTPS. Confirm the existing site's hostname still
returns its prior content before closing the change.

The application target is `nosblindtests:80`; the media target is
`blindtest-minio:9000`; NPM's own admin UI and MinIO's console stay private.
