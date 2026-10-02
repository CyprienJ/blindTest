import { Page } from '@playwright/test';
import { expect, test } from './fixtures/test';
import { installSession } from './support/auth';

const admin = {
  id: '22222222-2222-4222-8222-222222222222',
  username: 'site-admin',
  email: 'admin@example.com',
  role: 'ADMIN',
  avatarUrl: null,
};

function account(id: string, username: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    username,
    email: `${username}@example.com`,
    role: 'USER',
    emailVerified: true,
    createdAt: '2026-09-01T10:00:00Z',
    lastConnexion: null,
    gamesPlayed: 0,
    playlistCount: 0,
    ...overrides,
  };
}

async function mockUserApi(page: Page) {
  let accounts = [
    account(admin.id, 'site-admin', { role: 'ADMIN', createdAt: '2026-08-01T10:00:00Z' }),
    account('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice', {
      createdAt: '2026-09-20T10:00:00Z',
      gamesPlayed: 12,
    }),
    account('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob', { gamesPlayed: 3 }),
  ];
  await page.route('**/api/admin/invitations', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/admin/users**', async (route) => {
    const request = route.request();
    const id = request.url().split('/').pop();
    if (request.method() === 'GET') {
      await route.fulfill({ json: accounts });
    } else if (request.method() === 'PUT') {
      const updated = {
        ...accounts.find((candidate) => candidate.id === id)!,
        ...request.postDataJSON(),
      };
      accounts = accounts.map((candidate) => (candidate.id === id ? updated : candidate));
      await route.fulfill({ json: updated });
    } else {
      accounts = accounts.filter((candidate) => candidate.id !== id);
      await route.fulfill({ status: 204 });
    }
  });
}

test('an administrator sorts, edits, and deletes accounts', async ({ page }) => {
  await installSession(page, admin);
  await mockUserApi(page);

  await page.goto('/admin/users');
  const accounts = page.getByRole('region', { name: 'Accounts' });
  const rows = accounts.locator('tbody tr');
  await expect(rows).toHaveCount(3);
  await expect(rows.first()).toContainText('alice');

  await accounts.getByRole('button', { name: /Games/ }).click();
  await expect(rows.first()).toContainText('site-admin');
  await expect(accounts.getByRole('columnheader', { name: /Games/ })).toHaveAttribute(
    'aria-sort',
    'ascending',
  );

  await accounts.getByRole('button', { name: 'Edit bob' }).click();
  const dialog = page.getByRole('dialog', { name: 'Edit bob' });
  await dialog.getByLabel('Username').fill('robert');
  await dialog.getByLabel('Role').selectOption('ADMIN');
  await dialog.getByRole('button', { name: 'Save changes' }).click();
  await expect(dialog).toBeHidden();
  await expect(accounts.getByRole('row', { name: /robert/ })).toContainText('Admin');

  await expect(accounts.getByRole('button', { name: 'Delete site-admin' })).toBeDisabled();
  await accounts.getByRole('button', { name: 'Delete alice' }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Delete account' }).click();
  await expect(accounts.getByRole('status')).toHaveText(/alice was deleted\./);
  await expect(rows).toHaveCount(2);
});
