# ADR-0003: Use STOMP for game events from V1

- Status: Accepted
- Date: 2026-07-27
- Deciders: Product owner and engineering
- Related tickets: FND-06, SESSION-03 through SESSION-08
- Supersedes: N/A
- Superseded by: N/A

## Context

Gameplay needs round starts, private answer feedback, reveals, score updates, and
completion to reach the browser promptly. Polling in the solo release would create
a temporary contract and client architecture that must be replaced for
multiplayer. The repository already has authenticated Spring WebSocket/STOMP
infrastructure for import progress.

STOMP is a transport boundary only. Game rules must remain callable without a
broker or WebSocket runtime.

## Decision

V1 gameplay commands and events will use STOMP 1.2 over the existing authenticated
WebSocket endpoint. REST creates and retrieves sessions; STOMP carries commands
and server-emitted live state after connection.

Public session projections use session-scoped destinations. Participant-specific
discoveries, command results, and errors use authenticated user destinations and
must never be broadcast. Every handler derives the participant identity from the
authenticated principal, authorizes membership, validates payloads, and delegates
to an application service. Event contracts include a type, session identifier,
state version, server timestamp, and command identifier where applicable.

Application services publish through an event port. The initial adapter uses
Spring's in-process STOMP broker; domain code imports no STOMP types.

## Consequences

- V1 exercises the same real-time interaction model needed by multiplayer.
- Spring's existing authentication and messaging support can be reused.
- Reconnect, duplicate delivery, ordering, authorization, proxy timeouts, and
  backpressure require explicit tests and operational configuration.
- The simple broker and in-process event adapter limit horizontal scaling.
- Replacing delivery with a broker or Redis pub/sub changes an adapter rather than
  game rules or application event definitions.

## Alternatives Considered

- **HTTP polling for V1:** rejected because it adds latency and a disposable client
  contract.
- **Server-Sent Events plus REST commands:** rejected because it splits the live
  channel without a current operational advantage.
- **Raw WebSocket messages:** rejected because STOMP already supplies destinations,
  subscriptions, and Spring integration.
- **External broker immediately:** deferred until multiple backend instances or
  measured delivery requirements justify its operational cost.

## Follow-up

- Specify destination names, payload schemas, error semantics, and reconnect flow
  before implementing handlers.
- Test that private events cannot be subscribed to or received by another user.
- Configure and monitor WebSocket health, origin policy, heartbeats, payload size,
  and reverse-proxy timeouts.

## References

- [Roadmap technical direction](../plan.md#technical-direction)
- [Component diagram](../diagrams/01-composants.puml)
- [Gameplay sequence diagrams](../diagrams/04-sequences.puml)

