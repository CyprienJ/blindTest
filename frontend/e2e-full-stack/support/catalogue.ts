import { APIRequestContext, expect, Page } from '@playwright/test';

// Shared full-stack setup: invitation-only registration, first-administrator promotion, and a
// public playlist of deterministic fake imports (FakeProcessFactory always yields FIXTURE_TITLE).

export const FIXTURE_TITLE = 'Deterministic e2e track title';
export const PASSWORD = 'StrongPassword!1';

export async function issueInvitation(
  request: APIRequestContext,
  baseURL: string,
  email: string,
): Promise<string> {
  // Registration is invitation-only; the e2e backdoor issues one without an admin session.
  const response = await request.post(`${baseURL}/api/e2e/invitations`, { params: { email } });
  expect(response.ok()).toBe(true);
  return ((await response.json()) as { token: string }).token;
}

/** Registers a verified account from an invitation and leaves it signed in on /home. */
export async function registerFromInvitation(
  page: Page,
  request: APIRequestContext,
  baseURL: string,
  username: string,
): Promise<void> {
  const email = `${username}@example.test`;
  const inviteToken = await issueInvitation(request, baseURL, email);
  await page.goto(`/auth/register?invite=${encodeURIComponent(inviteToken)}`);
  await expect(page.getByLabel('Email')).toHaveValue(email);
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await expect(page.getByRole('button', { name: 'Create account' })).toBeEnabled({
    timeout: 15_000,
  });
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/home$/);
}

/**
 * Promotes the account through the e2e-only provisioning backdoor, after clearing any
 * administrator another spec file promoted on this shared stack, then signs in again so the
 * session carries the ADMIN role.
 */
export async function promoteAndSignIn(
  page: Page,
  request: APIRequestContext,
  baseURL: string,
  username: string,
): Promise<void> {
  const email = `${username}@example.test`;
  expect((await request.delete(`${baseURL}/api/e2e/admin-provisioning`)).ok()).toBe(true);
  const response = await request.post(`${baseURL}/api/e2e/admin-provisioning`, {
    params: { email },
  });
  expect((await response.json()).outcome).toBe('PROMOTED');
  await page.goto('/auth/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/home$/);
}

/** Imports and publishes `count` fake tracks, then puts them in a new public playlist. */
export async function publishPlaylist(page: Page, name: string, count: number): Promise<void> {
  await page.goto('/admin/tracks/import');
  for (let index = 1; index <= count; index++) {
    await page.getByRole('button', { name: 'YouTube link' }).click();
    await page
      .getByLabel('YouTube URL *')
      .fill(`https://www.youtube.com/watch?v=e2e-multi-${index}`);
    await page.getByRole('button', { name: '↓ Download music' }).click();
    await expect(page.getByRole('heading', { name: 'Choose the excerpt start' })).toBeVisible({
      timeout: 30_000,
    });
    await page.getByRole('radio', { name: '3 stars' }).click();
    await page.getByRole('button', { name: '✓ Validate selection' }).click();
    const publishButton = page.getByRole('button', { name: '✓ Publish track' });
    await expect(publishButton).toBeEnabled({ timeout: 15_000 });
    await publishButton.click();
    await expect(page.getByText('Track published', { exact: true })).toBeVisible();
    if (index < count) {
      await page.getByRole('button', { name: 'New import' }).click();
    }
  }

  await page.goto('/admin/playlists');
  await page.getByRole('button', { name: '＋ New playlist' }).first().click();
  await page.getByLabel('Playlist name').fill(name);
  await page.getByRole('button', { name: 'Create playlist' }).click();
  await expect(page.getByRole('heading', { name: name })).toBeVisible();
  const addButton = page.getByRole('button', { name: `Add ${FIXTURE_TITLE}` }).first();
  for (let index = 1; index <= count; index++) {
    await addButton.click();
    await expect(page.locator('.track-row')).toHaveCount(index);
  }
}
