# ADR-0007: Round title scores to the nearest integer with a 100-point minimum

- Status: Accepted
- Date: 2026-07-30
- Deciders: Product owner and engineering
- Related tickets: DG-01, GAME-06
- Supersedes: N/A
- Superseded by: N/A

## Context

The first-release solo game awards up to 1,000 points for a correct title, with
the score decreasing approximately linearly as the server-authoritative round
deadline approaches. Secondary answers, skips, and timeouts award no points.

The product still needed an exact rounding rule and a minimum score for a correct
title submitted before the deadline. Without a minimum, a valid last-moment
answer could display as zero points, making it indistinguishable from a timeout
or skip.

## Decision

For a correct title accepted before the server deadline, calculate:

```text
linearScore = 1000 * remainingTime / configuredRoundDuration
score = max(100, roundToNearestInteger(linearScore))
```

The linear score is clamped to the inclusive range from 0 to 1,000 before
rounding. Positive half values round upward, equivalent to
`floor(linearScore + 0.5)`.

A correct title accepted at the start can therefore award 1,000 points. Any
correct title accepted strictly before the deadline awards at least 100 points.
An answer at or after the deadline, a skip, a timeout, and every secondary answer
award zero points.

All timing uses the server's authoritative timestamps. Client countdown state
cannot change the awarded score.

## Consequences

- A correct in-time title is always visibly rewarded.
- Scores remain linear for the first 90 percent of elapsed round time.
- The final 10 percent creates a 100-point floor rather than continuing to zero.
- Integer scores are deterministic across backend tests, events, and history.
- Boundary tests must cover the start, midpoint, floor threshold, immediately
  before the deadline, at the deadline, and after the deadline.

## Alternatives Considered

- **Nearest integer with a one-point minimum:** preserves linearity almost to the
  deadline but gives very little reward for a valid late answer.
- **No minimum with downward rounding:** can award zero for a correct in-time
  answer and makes the displayed result ambiguous.
- **Round to larger increments:** simplifies displayed values but introduces
  wider discontinuities without a product benefit.

## Follow-up

- Implement the formula in GAME-06 using duration-independent integer-safe
  arithmetic.
- Store the awarded integer score and scoring-rule version in game snapshots and
  durable history.
- Add deterministic deadline and rounding-boundary tests.

## References

- [Roadmap solo game rules](../roadmap/product-decisions.md#solo-game-rules)
- [Decision gates](../roadmap/decision-gates.md#5-decision-gates)
- [ADR-0002](0002-server-authoritative-sessions.md)
