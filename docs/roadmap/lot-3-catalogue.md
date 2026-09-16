# 10. Lot 3 — Catalogue and Playlist Administration

[Up: Roadmap](../plan.md)

### Objective

Complete the current backend functionality and replace all catalogue/admin frontend
placeholders required for the first release.

Dependencies: Lot 2.

### Tickets

#### CAT-01 — Extend the track data model

Status: completed on 2026-07-30.

Work:

- add `album`;
- formalize `title` as the primary game answer;
- retain `artist`, `composer`, `work`, category, decade, image, duration;
- define published metadata completeness rules;
- snapshot-compatible DTOs.

Acceptance:

- film title and music title use the same documented primary field;
- optional secondary fields are represented consistently.

#### CAT-02 — Model answer aliases and excerpt metadata

Status: completed on 2026-07-30.

Decision: [ADR-0009](../adr/0009-admin-configured-playback-offset.md).

Work:

- persist manually configured aliases by answer type;
- support aliases for title, artist, composer, and album;
- validate duplicate/blank aliases;
- add an optional whole-second excerpt start offset, defaulting to zero;
- require 60 playable seconds after a non-zero offset and store no separate
  excerpt duration;
- expose admin CRUD without exposing internal normalization.

Acceptance:

- administrators can manage alternative valid answers;
- published tracks always have at least one primary title answer.

#### CAT-03 — Harden YouTube import requests

Status: completed on 2026-07-30.

Work:

- accept supported YouTube URL forms only;
- validate metadata and field sizes;
- reject duplicate active imports;
- enforce import timeout and maximum output size;
- sanitize provider errors;
- keep command arguments as a list without a shell.

Acceptance:

- malformed/unsupported URLs fail before process startup;
- import cannot write outside its temporary directory.

#### CAT-04 — Complete import status and publication workflow

Status: completed on 2026-07-30.

Retention decision: [ADR-0011](../adr/0011-data-and-rejected-media-retention.md).

Work:

- define allowed status transitions;
- make validate/reject idempotent;
- delete or retain rejected objects according to DG-05;
- expose progress, safe error, preview availability, and timestamps;
- test reconnect/poll fallback when WebSocket progress was missed.

Acceptance:

- only `DONE` tracks can be published; `DONE` or already `PUBLISHED` tracks can be
  rejected;
- only `PUBLISHED` tracks appear in public playlists/catalogue.

#### CAT-05 — Implement the admin track catalogue UI

Status: completed on 2026-07-30.

Work:

- list/filter/paginate all statuses;
- edit metadata and aliases;
- preview audio;
- validate, reject, and delete with confirmation;
- show import and storage errors.

Acceptance:

- the placeholder is replaced by the reference design;
- all actions enforce loading, empty, success, and failure states.

#### CAT-06 — Implement the admin import UI

Status: completed on 2026-07-30.

Work:

- URL-only import start with automatic YouTube metadata extraction;
- editable extracted metadata and a one-handle 60-second excerpt selector with
  immediate seek-and-play feedback;
- bounded server-generated waveform peaks rendered as an interactive SVG with the
  selected 60-second window overlaid;
- album and aliases;
- progress through STOMP with polling recovery;
- selected-excerpt preview followed by explicit publication;
- prevent duplicate submission;
- match the design reference.

Acceptance:

- an administrator completes the full import-to-publication journey from the UI;
- normal users cannot enter or invoke the flow.

#### CAT-07 — Complete playlist ordering APIs

Status: completed on 2026-08-01.

Work:

- create/update/delete public playlists;
- add/remove/reorder published tracks;
- prevent duplicates;
- validate atomic order updates;
- define behavior when a published track is later rejected/deleted.

Acceptance:

- order is deterministic and transactionally updated;
- playlist detail never exposes unavailable audio metadata.

#### CAT-08 — Implement playlist editor UI

Status: completed on 2026-08-02.

Work:

- playlist metadata;
- published-track search;
- add/remove/reorder;
- unsaved-change warning;
- optimistic UI only where rollback is safe.

Acceptance:

- an administrator can create a playable ordered playlist without direct API use.

#### CAT-09 — Implement public catalogue and playlist selection data

Status: completed on 2026-08-02.

Work:

- public list/detail endpoints aligned with OpenAPI;
- expose published metadata and track counts, never storage keys or YouTube URLs;
- define cache behavior;
- provide the game configuration page with eligible playlists.

Acceptance:

- unauthenticated visitors may view public playlists;
- only an authenticated user may create a game.

#### CAT-10 — Add catalogue/admin automated coverage

Status: completed on 2026-08-04.

Work:

- service and PostgreSQL integration tests;
- Playwright admin import and playlist workflows with deterministic adapters;
- authorization and failure paths;
- OpenAPI contract checks.

Acceptance:

- each admin journey has a browser test and backend boundary coverage.

### Lot exit criteria

- an administrator can import, preview, publish, and organize tracks;
- a public ordered playlist can be selected for a game;
- all required admin placeholders are replaced.
