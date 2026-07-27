# Testing and Coverage

## Backend coverage

Run the backend checks and generate the JaCoCo report with:

```bash
cd backend
mvn --batch-mode checkstyle:check test
```

The readable HTML report is written to `backend/target/site/jacoco/index.html`.
The machine-readable XML report is written to
`backend/target/site/jacoco/jacoco.xml`. CI uploads the complete report as the
`backend-coverage` artifact on every backend run and retains it for 14 days.

### Baseline

The baseline recorded on 2026-07-27 with Java 21 and 113 passing tests is:

| Scope | Line coverage | Branch coverage |
| --- | ---: | ---: |
| All backend production code | 793 / 953 (83.2%) | 96 / 184 (52.2%) |
| `com.blindtest.security` | 56 / 61 (91.8%) | 5 / 6 (83.3%) |
| `com.blindtest.websocket` | 17 / 17 (100.0%) | 8 / 10 (80.0%) |
| `com.blindtest.service` | 264 / 350 (75.4%) | 43 / 72 (59.7%) |

The overall baseline is informational. There is deliberately no global coverage
threshold: generated accessors, DTOs, and framework wiring should not attract
low-value tests merely to satisfy one number.

JaCoCo instead enforces regression floors on critical packages during the Maven
`test` phase:

| Package | Minimum lines | Minimum branches |
| --- | ---: | ---: |
| `com.blindtest.security` | 90% | 80% |
| `com.blindtest.websocket` | 95% | 80% |
| `com.blindtest.service` | 75% | 59% |

When new critical domain packages are delivered, add package-specific floors
based on meaningful behavior tests. Raise an existing floor when tests establish
a durable higher baseline; do not lower one to make an unrelated change pass.
