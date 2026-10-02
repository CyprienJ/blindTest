import { Page } from '@playwright/test';
import { expect, test } from './fixtures/test';
import { installSession } from './support/auth';

const admin = {
  id: '22222222-2222-4222-8222-222222222222',
  username: 'invite-admin',
  email: 'admin@example.com',
  role: 'ADMIN',
  avatarUrl: null,
};

function invitation(id: string, email: string, status: string) {
  return {
    id,
    email,
    status,
    invitedBy: 'invite-admin',
    createdAt: '2026-10-02T08:00:00Z',
    expiresAt: '2026-10-04T08:00:00Z',
    acceptedAt: status === 'ACCEPTED' ? '2026-10-02T09:00:00Z' : null,
    revokedAt: null,
  };
}

async function mockInvitationApi(page: Page) {
  const invitations = [invitation('accepted-id', 'joined@example.com', 'ACCEPTED')];
  const requests: { method: string; url: string; body: unknown }[] = [];
  await page.route('**/api/admin/invitations**', async (route) => {
    const request = route.request();
    requests.push({ method: request.method(), url: request.url(), body: request.postDataJSON() });
    if (request.method() === 'GET') {
      await route.fulfill({ json: invitations });
      return;
    }
    if (request.method() === 'POST' && request.url().endsWith('/api/admin/invitations')) {
      const created = invitation(
        'new-id',
        (request.postDataJSON() as { email: string }).email,
        'PENDING',
      );
      invitations.unshift(created);
      await route.fulfill({ status: 201, json: created });
      return;
    }
    if (request.method() === 'DELETE') {
      invitations[0] = { ...invitations[0], status: 'REVOKED' };
      await route.fulfill({ status: 204 });
      return;
    }
    await route.fulfill({ status: 201, json: invitations[0] });
  });
  return requests;
}

test('an administrator invites a player and revokes the pending invitation', async ({ page }) => {
  await installSession(page, admin);
  await page.route('**/api/admin/users', (route) => route.fulfill({ json: [] }));
  const requests = await mockInvitationApi(page);

  await page.goto('/admin/users');
  const section = page.getByRole('region', { name: 'Invitations', exact: true });
  await expect(section).toBeVisible();
  await expect(page.getByRole('cell', { name: 'joined@example.com', exact: true })).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Revoke invitation to joined@example.com' }),
  ).toHaveCount(0);

  await page.getByLabel('Email address').fill('friend@example.com');
  await page.getByRole('button', { name: 'Send invitation' }).click();

  await expect(section.getByRole('status')).toHaveText(/Invitation sent to friend@example.com\./);
  await expect(page.getByRole('cell', { name: 'friend@example.com', exact: true })).toBeVisible();
  expect(requests).toContainEqual(
    expect.objectContaining({ method: 'POST', body: { email: 'friend@example.com' } }),
  );

  await page.getByRole('button', { name: 'Revoke invitation to friend@example.com' }).click();
  await expect(section.getByRole('status')).toHaveText(/was revoked\./);
  await expect(
    page.getByRole('button', { name: 'Revoke invitation to friend@example.com' }),
  ).toHaveCount(0);
});
