# 19. First Recommended Execution Sequence

[Up: Roadmap](../plan.md)

Completed, in this order:

1. FND-01 through FND-08;
2. TEST-01 through TEST-05;
3. MEDIA-01 through MEDIA-07 and MEDIA-09;
4. CAT-01 through CAT-10;
5. decision gates DG-01 through DG-09;
6. GAME-01 through GAME-10;
7. HOME-01, then HOME-02;
8. SESSION/HIST tickets;
9. UI tickets (Lot 6) and Lot 7 (E2E-01 through E2E-07);
10. OPS-01 through OPS-03 (production runs at `nosblindtests.fr`).

Remaining, re-sequenced on 2026-10-02:

11. CAT-11 (validated; awaiting a production upload by the product owner);
12. [Lot P2 — Multiplayer and guests](lot-p2-multiplayer.md), MP-01 through MP-10;
13. TEST-06, then OPS-04 through OPS-09 (Lot 8);
14. Lot 9 without REL-01: REL-02 acceptance, REL-04 recovery rehearsal into an
    isolated target, then the remaining REL-06 launch baseline;
15. MEDIA-08 when real legacy files require migration, before removing `/media`.

The product owner accepted the unguarded push-to-`main` deployment until TEST-06
and OPS-09. Backups (OPS-05) may start earlier if production data grows.
