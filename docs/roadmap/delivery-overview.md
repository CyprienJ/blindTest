# 6. Delivery Overview

[Up: Roadmap](../plan.md)

```text
LOT 0  Repository, security, and decision baseline
  |
  +--> LOT 1  Automated quality foundation
  |
  +--> LOT 2  S3/MinIO media foundation
  |      |
  |      +--> LOT 3  Complete catalogue and playlist administration
  |                    |
  +--> LOT 4  Pure solo-game domain
           |
           +--> LOT 5  Session, WebSocket, and history backend
                         |
                         +--> LOT 6  Solo-game frontend and summary

LOT 7  Full-stack validation and hardening (progresses across Lots 2-6)
LOT 8  VPS operations and deployment readiness
LOT 9  Beta validation and production launch
```

Lots may overlap when their dependencies permit. The critical product path is:

```text
security -> storage -> catalogue -> game domain -> session backend
-> game frontend -> full-stack validation -> production
```
