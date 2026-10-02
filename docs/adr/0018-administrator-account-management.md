# ADR-0018: Let administrators manage accounts

- Status: Accepted
- Date: 2026-10-02
- Deciders: Product owner and engineering
- Related tickets: FND-08
- Supersedes: ADR-0010 in part (general role management was deferred)
- Superseded by: N/A

## Context

Registration is invitation-only ([ADR-0017](0017-invitation-only-registration.md)),
so administrators decide who has an account. They also need to see every account,
correct usernames and emails, grant or remove the administrator role, and delete
accounts. ADR-0010 had deferred general role management beyond the first release.

## Decision

- `GET /api/admin/users` returns every account with its role, verification state,
  sign-up and last sign-in times, completed-game count, and owned-playlist count.
  Invitation-only registration keeps the list small, so the browser receives the
  full list and sorts, filters, and searches it locally.
- `PUT /api/admin/users/{id}` changes username, email, and role. Uniqueness rules
  match registration. A role change deletes the account's refresh tokens, so the
  new role applies at the next sign-in.
- `DELETE /api/admin/users/{id}` removes the account and its refresh tokens.
  Completed-game history is kept and anonymized by the existing foreign key.
- An administrator cannot change their own role or delete their own account.
  This guarantees that at least one administrator always remains.
- An account that owns playlists cannot be deleted, because the playlist foreign key
  cascades and would silently remove public playlists. Its playlists must be
  deleted first.

## Consequences

- Access tokens are stateless and live for up to 15 minutes, so a deleted or
  demoted account keeps its already-issued access token until it expires. Refresh
  is refused immediately.
- Deleting an administrator also deletes their import-job records through the
  existing cascade. Imported tracks are not affected.
- Email changes do not send a confirmation; the administrator vouches for the new
  address.
- Changes are not audited yet.

## Alternatives Considered

- **Server-side paging and sorting:** unnecessary at invitation-only scale; revisit
  if the account count grows into the thousands.
- **Checking the account on every request:** would make deletion and demotion
  immediate, but adds a database read to every authenticated request.
- **Reassigning playlists on deletion:** more flexible but needs a target-picker UI;
  refusing the deletion is the safe first step.

## Follow-up

- Add an audit trail for account changes if more administrators are added.
- Consider immediate token revocation if the 15-minute window becomes a problem.

## References

- [Authentication](../backend/authentication.md)
- [ADR-0010](0010-first-administrator-operator-command.md)
