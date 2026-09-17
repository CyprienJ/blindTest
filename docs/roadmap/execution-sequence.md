# 19. First Recommended Execution Sequence

[Up: Roadmap](../plan.md)

Start in this order:

1. FND-01 through FND-06;
2. TEST-01 through TEST-05;
3. MEDIA-01 through MEDIA-07 and MEDIA-09;
4. CAT-01 through CAT-10;
5. decision gates DG-01 through DG-09 are resolved;
6. GAME-01 through GAME-10;
7. HOME-01, then HOME-02;
8. SESSION/HIST tickets, starting again at SESSION-03;
9. remaining UI tickets;
10. complete full-stack and operations lots;
11. beta, recovery rehearsal, then production launch;
12. MEDIA-08 when real legacy files require migration, before removing `/media`.

TEST-06 should be completed before beta deployment. Operations work for secrets,
backups, and reverse proxy should begin early rather than waiting for the last
product ticket.
