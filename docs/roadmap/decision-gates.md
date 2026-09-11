# 5. Decision Gates

[Up: Roadmap](../plan.md)

These choices required explicit product-owner approval before their blocking
tickets could start. All nine gates are now resolved and recorded in accepted
ADRs; a change must supersede the corresponding ADR.

| Gate | Decision | Effect |
|---|---|---|
| DG-01 | Resolved: nearest-integer rounding with a 100-point minimum ([ADR-0007](../adr/0007-title-score-rounding-and-minimum.md)) | GAME-06 unblocked by this gate |
| DG-02 | Resolved: 5–30 tracks, 10–60 seconds, defaults 15/30 ([ADR-0008](../adr/0008-solo-game-configuration-limits.md)) | GAME-02 and UI-02 unblocked by this gate |
| DG-03 | Resolved: optional administrator-configured offset, default zero ([ADR-0009](../adr/0009-admin-configured-playback-offset.md)) | CAT-02 and GAME-08 unblocked by this gate |
| DG-04 | Resolved: promote one verified account with a one-shot operator command ([ADR-0010](../adr/0010-first-administrator-operator-command.md)) | FND-05 unblocked by this gate |
| DG-05 | Resolved: histories 24 months, imports 30 days, rejected media 7 days ([ADR-0011](../adr/0011-data-and-rejected-media-retention.md)) | HIST-01 and OPS-08 unblocked by this gate |
| DG-06 | Resolved for now: operator is assumed to hold the required rights; no in-app policy workflow ([ADR-0012](../adr/0012-assume-operator-holds-content-rights.md)) | REL-03 unblocked by this gate |
| DG-07 | Resolved for now: daily VPS backups, nominal RPO 24 h, best-effort RTO ([ADR-0013](../adr/0013-rely-on-daily-vps-backups-for-now.md)) | OPS-05 and OPS-06 unblocked by this gate |
| DG-08 | Resolved: uniform selection without replacement and shuffled order ([ADR-0014](../adr/0014-randomize-game-track-selection-and-order.md)) | GAME-03 unblocked by this gate |
| DG-09 | Resolved: automatic transition after 4 seconds with owner advance action ([ADR-0015](../adr/0015-four-second-automatic-reveal-transition.md)) | UI-07 and SESSION-05 unblocked by this gate |

Each decision must be written as an ADR or product decision note before related
implementation begins.
