# Frontend guide

[Up: Project overview](../../README.md) · [Architecture](../architecture.md)

The Angular 22 browser application uses standalone components, lazy feature routes,
RxJS services, and shared global styling. Nginx serves its production build;
Angular's development server proxies backend traffic during local development.

Angular 22 makes `OnPush` the default change detection. Components that update plain
fields from HTTP or WebSocket callbacks must declare `ChangeDetectionStrategy.Eager`,
or their view never refreshes; only components rendering from inputs, signals, or
the `async` pipe may use `OnPush`. Layout components wrapping `<router-outlet>` stay
`Eager`.

## Routes and user experience

The [route tree](../../frontend/src/app/app.routes.ts) exposes public entry and
authentication pages, then places player routes inside a guarded application shell.
The shell presents the account identity, navigation, role-aware links, and sign-out.
Administrator routes additionally use `adminGuard`; denied access returns to
`/home`. Authentication is checked on child navigation as well as initial entry.
Failed refresh recovery clears local account state and redirects to login with a
return URL. A refresh response without a stored account also requires login,
because the refresh API supplies only an access token.

The reference-style desktop top bar links to game creation and the public playlist
section of that screen. Its administrator link follows the current account stream;
friends and history remain disabled. Links have visible keyboard focus and active
page semantics, and the header wraps on narrow screens. Sign-out ignores repeated
clicks while pending and clears local state even when the logout request fails.

| Area | Current implementation |
|---|---|
| Public/auth | Home, login, invitation-only registration/Turnstile, verification, password recovery/reset |
| Authenticated home | Player actions and role-aware administration navigation |
| `/lobby/create` | Public playlist selection, ordered preview, limits, REST session creation |
| `/game/:sessionId` | Game state, answer/skip controls, score, audio, reconnect and basic finish view |
| `/end-screen/:sessionId` | Refresh-safe durable summary: score, per-round outcomes, replay action |
| Administration | Track catalogue (sortable by category or date added, streaming-link column), import/review/excerpt workflow, playlist CRUD and ordering, Users page (sortable accounts with edit/delete, invitations) |
| Other feature routes | Several remain placeholders; see [current status](../product/status.md) |

## State and service boundaries

`core/services/auth.service.ts` owns local identity and access-token state. The JWT
interceptor attaches authorization and participates in refresh recovery. Guards
protect navigation but cannot replace backend authorization.

`GameSessionService` calls the session REST endpoint and keeps the current session
in memory and `sessionStorage`. `GameSocketService` handles STOMP connection,
commands, event streams, and snapshot recovery. `AudioPlaybackService` owns the
round's audio element and playback timing. Components translate those streams
into visible UI state. See [gameplay client](gameplay.md) for the detailed flow.

Administrator imports combine live STOMP progress with durable HTTP polling.
The shared excerpt editor renders backend-generated waveform peaks, selects the
start and a 5–60 second window, and previews the window in a loop; the import
review and the track editor both use it. Both forms also set the difficulty and the
six streaming links, validated against each platform's domain.
The shared cover picker searches again 700 ms after the title, artist, or album
changes, shows up to six suggestions with thumbnails fetched as authorized blobs,
and offers upload, keep, and "no cover"; the choice is sent with the track update.
Playlist editing supports drag and keyboard ordering, rollback on failed
mutations, and unsaved-change protection.
Its "Add tracks" panel lists only published tracks outside the playlist (excluded
by the server), filters by text, category, decade, and difficulty, loads 20 more
on demand, shows difficulty and duration, and previews each track's game excerpt.

## Source map and deeper reading

- [Core](../../frontend/src/app/core): models, services, guards, interceptors, shell.
- [Features](../../frontend/src/app/features): routed screens and feature components.
- [Shared](../../frontend/src/app/shared): reusable UI pieces; some are scaffolds.
- [Gameplay client](gameplay.md): command/event flow, recovery, and audio.
- [Frontend quick start](../../frontend/README.md).
- [Local development](../development/README.md) and [testing](../testing.md).
- [UI reference](../Blindtest/Blindtest%20Views.html): visual intent and adjacent JSX.
  Code and validated product decisions take precedence over reference screens.
- [Frontend roadmap](../roadmap/lot-6-frontend.md): acceptance criteria, distinct from
  this code inventory.
