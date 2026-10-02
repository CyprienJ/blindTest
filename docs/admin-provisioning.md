# First-administrator provisioning

[Up: Operations](operations/README.md)

Registration is invitation-only, and only administrators can send invitations.
The first administrator is therefore created in two operator steps: a bootstrap
invitation, then promotion of the account created from it. There is no bootstrap
HTTP endpoint, default administrator, or password accepted by either command.

## Preconditions

1. Deploy the backend version containing Flyway migration V22.
2. Start the production stack and confirm that PostgreSQL and the backend are
   healthy.
3. Confirm that the Compose project and environment file target the intended
   production database.

## Bootstrap invitation

While no administrator exists, run:

```bash
docker compose run --rm --no-deps backend invite-first-admin
```

Enter the future administrator's email at the prompt. The command prints a
registration link to the terminal only; it does not email or log it. Open the link
within 48 hours, choose a username and password, and the account is created
verified. Running the command again replaces the previous link. It exits with `2`
when an administrator already exists or the address already has an account.

## Promotion

From the deployment directory, run a one-shot backend container:

```bash
docker compose run --rm --no-deps backend provision-first-admin
```

Enter the verified account email at the prompt. The email is read from standard
input so it is not exposed in the process arguments or repeated in command output.

The command exits with:

- `0` when it promotes the account, or when that account is already the sole
  administrator;
- `2` when input is invalid, the account does not exist, its email is not
  verified, or another administrator already exists;
- the normal Spring Boot startup failure when the database configuration is
  invalid, PostgreSQL is unavailable, or migrations cannot run.

Every business outcome is persisted in `admin_provisioning_audit` with its time
and, where an account was found, its user UUID. Submitted email addresses,
passwords, tokens, and connection secrets are not stored in this audit table or
written by the command logger.

## Verification

The promoted user must sign out and sign in again so the new access token contains
the `ADMIN` role. Verify that an authenticated request to
`GET /api/admin/tracks` returns `200`; a stale pre-promotion token will still be a
user token and can return `403`.

Running the command again for the same account is safe and reports that it is
already the sole administrator. Running it for another account is refused.

## Recovery

- `account not found`: check the intended environment and that the account was
  created from the bootstrap invitation, then run the command again; do not pass
  the email as a command-line argument.
- `invitation refused`: an administrator already exists, so ask them to invite
  the person from the Users admin page instead.
- `account email is not verified`: finish the existing verification flow, then
  retry.
- `an administrator already exists`: stop. Use password reset to recover access
  to that administrator. Do not update roles with ad hoc SQL.
- database or migration startup failure: leave the application data unchanged,
  repair connectivity or configuration, verify Flyway, and rerun the same
  command.

The command provisions only the first administrator. General role management is
outside the first-release scope.
