# Product guide

[Up: Project overview](../../README.md)

Blindtest turns a curated music catalogue into timed title-guessing games.
There are two current account roles: player and administrator. Administrators
also have access to player features.

## Player journey

A player creates an account, passes the anti-bot challenge, verifies their email,
and signs in. From the authenticated home page they configure a solo game using a
public playlist, a track count, and a round duration. Each round plays an audio
excerpt and accepts typed guesses until the title is found, the player skips,
or the server deadline expires.

The backend reveals the answer and advances after a short reveal interval.
Completed games are stored independently of subsequent catalogue changes.
The separate persisted-summary browser page is still unfinished.

## Administrator journey

An operator promotes the first verified administrator using the
[provisioning command](../admin-provisioning.md). That administrator submits a
YouTube URL, follows import progress, and previews the resulting audio. They can
edit metadata and accepted alternative answers, choose the excerpt offset, and
publish or reject the track. Published tracks can be arranged in public playlists.

## Go deeper

- [Current status and limitations](status.md): what exists in code and what remains.
- [Gameplay rules](gameplay.md): configuration, matching, scoring, and outcomes.
- [Catalogue and imports](../backend/catalogue.md): how administration changes data.
- [First-release journeys](../roadmap/user-journeys.md): intended acceptance flows.
- [Validated decisions](../roadmap/product-decisions.md): authoritative release scope.
- [Deferred features](../roadmap/deferred-features.md): what comes after solo V1.
