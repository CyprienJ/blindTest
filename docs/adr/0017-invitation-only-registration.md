# ADR-0017: Make registration invitation-only

- Status: Accepted
- Date: 2026-10-02
- Deciders: Product owner and engineering
- Related tickets: FND-07
- Supersedes: ADR-0010 in part (how the first administrator account is created)
- Superseded by: N/A

## Context

Anyone could create an account through public registration. The product owner
wants a closed audience: only people an administrator chooses may hold an account.
Accounts created through the former public flow are not wanted either.

## Decision

- Registration requires a one-time invitation. There is no other way to create an
  account through the application.
- Only administrators issue invitations, from `POST /api/admin/invitations`. They
  can list, resend, and revoke them.
- An invitation targets one email address, expires after **48 hours**, and can be
  used once. Issuing a new invitation for the same address revokes the open one.
  Inviting an address that already has an account is refused.
- The invitation link carries a random 256-bit token. Only its SHA-256 hash is
  stored. It is an opaque database token, not a JWT, so it can be revoked and
  consumed exactly once with a single conditional `UPDATE`.
- The invitation fixes the account email. Following the link proves ownership of
  the mailbox, so the account is created already verified, with no verification
  email. The new player is signed in immediately and receives the welcome email.
- Registration keeps the Turnstile check and still always creates `USER`
  accounts.
- Flyway migration V22 deletes every account whose role is not `ADMIN`.
  Refresh tokens cascade and history participants are anonymized through the
  existing foreign keys.
- Bootstrap: while no administrator exists, the operator command
  `invite-first-admin` prints a registration link for the future administrator to
  the operator terminal only. That person registers through it, then the existing
  `provision-first-admin` command from ADR-0010 promotes the account. The rest of
  ADR-0010 is unchanged.

## Consequences

- The public home and login pages no longer advertise sign-up. Without a usable
  invitation, `/auth/register` explains that access is invitation-only.
- The V22 deletion is irreversible, apart from restoring a database backup.
- Email verification and the `verify-email` endpoint remain for accounts that are
  still unverified, but new accounts no longer need them.
- Invitation tokens appear in links in invitation emails. The frontend serves
  `Referrer-Policy: strict-origin-when-cross-origin`, so the token is not sent
  to other origins.
- Timestamps are stored as UTC and returned as ISO-8601 instants.

## Alternatives Considered

- **Signed JWT invitations:** stateless, but they cannot be revoked or limited to
  one use without extra storage.
- **Allow-listing email domains:** does not match a hand-picked audience.
- **Letting players invite friends:** rejected for now; only administrators invite.
- **Keeping existing non-admin accounts:** rejected by the product owner.

## Follow-up

- Remove Turnstile from registration if invitation tokens prove sufficient.
- Revisit player-sent invitations together with the post-V1 social features.

## References

- [Authentication](../backend/authentication.md)
- [First-administrator provisioning](../admin-provisioning.md)
- [ADR-0010](0010-first-administrator-operator-command.md)
- Migration `backend/src/main/resources/db/migration/V22__invitation_only_registration.sql`
