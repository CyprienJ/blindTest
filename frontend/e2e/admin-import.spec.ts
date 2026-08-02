import { expect, Page, test } from '@playwright/test';

const jobId = '99999999-9999-4999-8999-999999999999';
const trackId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const admin = {
  id: '22222222-2222-4222-8222-222222222222',
  username: 'catalogue-admin',
  email: 'admin@example.com',
  role: 'ADMIN',
  avatarUrl: null,
};
const track = {
  id: trackId,
  title: 'Billie Jean',
  artist: 'Michael Jackson',
  album: 'Thriller',
  composer: 'Michael Jackson',
  work: null,
  category: 'Pop',
  decade: 1980,
  durationSeconds: 294,
  excerptStartSeconds: 0,
  imageUrl: null,
  audioUrl: null,
  audioChecksum: 'a'.repeat(64),
  audioSizeBytes: 1234,
  youtubeUrl: 'https://www.youtube.com/watch?v=Zi_XLOBDo_Y',
  status: 'PENDING',
  createdAt: '2026-07-30T12:00:00',
};

function job(status: string, progress: number, errorMessage: string | null = null) {
  return {
    jobId,
    status,
    progress,
    errorMessage,
    previewAvailable: status === 'DONE',
    createdAt: '2026-07-30T12:00:00',
    updatedAt: '2026-07-30T12:01:00',
    completedAt: status === 'DONE' || status === 'FAILED' ? '2026-07-30T12:01:00' : null,
    track,
  };
}

async function installAdmin(page: Page): Promise<void> {
  await page.addInitScript((user) => {
    localStorage.setItem('access_token', 'admin-token');
    localStorage.setItem('current_user', JSON.stringify(user));
  }, admin);
  await page.route('**/ws/**', (route) => route.abort());
}

test.describe('administrator YouTube import', () => {
  test.beforeEach(async ({ page }) => {
    await installAdmin(page);
    await page.route('**/api/admin/tracks/*/waveform', (route) =>
      route.fulfill({
        json: {
          durationSeconds: 294,
          peaks: [12, 30, 65, 100, 72, 38, 80, 55, 20, 8],
        },
      }),
    );
  });

  test('completes import, polling recovery, aliases, preview and publication', async ({ page }) => {
    let startRequests = 0;
    let pollRequests = 0;
    let importBody: Record<string, unknown> = {};
    let aliasBody: Record<string, unknown> = {};
    let updateBody: Record<string, unknown> = {};

    await page.route('**/api/admin/tracks/import', async (route) => {
      startRequests++;
      importBody = route.request().postDataJSON();
      await new Promise((resolve) => setTimeout(resolve, 100));
      await route.fulfill({ status: 202, json: { jobId } });
    });
    await page.route(`**/api/admin/tracks/import/${jobId}`, async (route) => {
      pollRequests++;
      await route.fulfill({
        json: pollRequests === 1 ? job('PROCESSING', 72) : job('DONE', 100),
      });
    });
    await page.route(`**/api/admin/tracks/${trackId}/aliases`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ json: [] });
      } else {
        aliasBody = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          json: { id: 'alias-id', ...aliasBody },
        });
      }
    });
    await page.route(`**/api/admin/tracks/${trackId}`, async (route) => {
      updateBody = route.request().postDataJSON();
      await route.fulfill({ json: { ...track, ...updateBody } });
    });
    await page.route(`**/api/admin/tracks/${trackId}/preview-url`, (route) =>
      route.fulfill({
        json: { url: '/media/import-preview.mp3', expiresAt: '2026-07-30T18:00:00Z' },
      }),
    );
    await page.route('**/media/import-preview.mp3', (route) =>
      route.fulfill({ contentType: 'audio/mpeg', body: '' }),
    );
    await page.route(`**/api/admin/tracks/${trackId}/validate`, (route) =>
      route.fulfill({ json: { ...track, status: 'PUBLISHED' } }),
    );

    await page.goto('/admin/tracks/import');
    await expect(page.getByRole('heading', { name: 'Import from YouTube' })).toBeVisible();
    await page.getByLabel('YouTube URL *').fill('https://youtu.be/Zi_XLOBDo_Y');

    const startButton = page.getByRole('button', { name: /Download music/ });
    await startButton.click();
    await expect(startButton).toBeDisabled();
    expect(startRequests).toBe(1);
    expect(importBody).toEqual({ youtubeUrl: 'https://youtu.be/Zi_XLOBDo_Y' });

    await expect(page.getByText('Processing · 72%.')).toBeVisible();
    await expect(page.getByText('Ready for review').first()).toBeVisible({ timeout: 6000 });
    await expect(page.getByLabel('Title *')).toHaveValue('Billie Jean');
    await expect(page.locator('.excerpt-editor svg path')).toBeVisible();
    await expect(page.locator('.excerpt-editor svg rect')).toHaveAttribute('width', /204/);
    const livePreview = page.locator('audio');
    await expect(livePreview).toBeVisible();
    await livePreview.evaluate((audio) => {
      audio.play = () => {
        audio.dataset['playedFromSlider'] = 'true';
        return Promise.resolve();
      };
    });
    await page.getByLabel('Title *').fill('Billie Jean (edited)');
    await page.getByRole('button', { name: 'Add alias' }).click();
    await page.getByRole('textbox', { name: 'Alternative answer' }).fill('Billie-Jean');
    await page.getByLabel('Excerpt start').fill('30');
    await expect(livePreview).toHaveAttribute('data-played-from-slider', 'true');
    await page.getByRole('button', { name: 'Validate selection' }).click();

    await expect(page.getByText('Saved title alias.')).toBeVisible();
    expect(aliasBody).toEqual({ type: 'TITLE', value: 'Billie-Jean' });
    expect(updateBody['title']).toBe('Billie Jean (edited)');
    expect(updateBody['excerptStartSeconds']).toBe(30);

    await expect(page.locator('audio')).toBeVisible();
    await page.getByRole('button', { name: 'Publish track' }).click();
    await expect(page.getByText('Track published', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Open catalogue' })).toBeVisible();
  });

  test('restores a missed WebSocket completion from session storage', async ({ page }) => {
    await page.addInitScript(
      ({ activeJob, aliases }) => {
        sessionStorage.setItem('active_admin_import_job', activeJob);
        sessionStorage.setItem('active_admin_import_aliases', JSON.stringify(aliases));
      },
      { activeJob: jobId, aliases: [] },
    );
    await page.route(`**/api/admin/tracks/import/${jobId}`, (route) =>
      route.fulfill({ json: job('DONE', 100) }),
    );

    await page.goto('/admin/tracks/import');
    await expect(page.getByText('Recovering the active import from the server.')).toBeVisible();
    await expect(page.getByLabel('Title *')).toHaveValue('Billie Jean');
    await expect(page.getByRole('button', { name: 'Validate selection' })).toBeEnabled();
    await expect(page.getByRole('button', { name: 'Publish track' })).toBeDisabled();
  });

  test('unlocks publication only after the selected excerpt preview is ready', async ({ page }) => {
    await page.route('**/api/admin/tracks/import', (route) =>
      route.fulfill({ status: 202, json: { jobId } }),
    );
    await page.route(`**/api/admin/tracks/import/${jobId}`, (route) =>
      route.fulfill({ json: job('DONE', 100) }),
    );
    await page.route(`**/api/admin/tracks/${trackId}/aliases`, (route) =>
      route.fulfill({ json: [] }),
    );
    await page.route(`**/api/admin/tracks/${trackId}`, (route) =>
      route.fulfill({ json: { ...track, ...route.request().postDataJSON() } }),
    );
    await page.route(`**/api/admin/tracks/${trackId}/preview-url`, (route) =>
      route.fulfill({ json: { url: '/media/preview.mp3', expiresAt: '2026-07-30T18:00:00Z' } }),
    );
    await page.route('**/media/preview.mp3', (route) =>
      route.fulfill({ contentType: 'audio/mpeg', body: '' }),
    );
    await page.route(`**/api/admin/tracks/${trackId}/validate`, (route) =>
      route.fulfill({ json: { ...track, status: 'PUBLISHED' } }),
    );

    await page.goto('/admin/tracks/import');
    await page.getByLabel('YouTube URL *').fill('https://youtu.be/Zi_XLOBDo_Y');
    await page.getByRole('button', { name: /Download music/ }).click();

    const publishButton = page.getByRole('button', { name: 'Publish track' });
    await expect(publishButton).toBeDisabled();
    await page.getByRole('button', { name: 'Validate selection' }).click();
    await expect(page.locator('audio')).toBeVisible();
    await expect(publishButton).toBeEnabled();
    await publishButton.click();
    await expect(page.getByText('Track published', { exact: true })).toBeVisible();
  });

  test('shows safe import failures and allows another attempt', async ({ page }) => {
    await page.route('**/api/admin/tracks/import', (route) =>
      route.fulfill({ status: 202, json: { jobId } }),
    );
    await page.route(`**/api/admin/tracks/import/${jobId}`, (route) =>
      route.fulfill({
        json: job('FAILED', 34, 'The media provider could not process this video'),
      }),
    );

    await page.goto('/admin/tracks/import');
    await page.getByLabel('YouTube URL *').fill('https://youtu.be/Zi_XLOBDo_Y');
    await page.getByRole('button', { name: /Download music/ }).click();

    await expect(page.getByRole('alert')).toContainText(
      'The media provider could not process this video',
    );
    await page.getByRole('button', { name: 'Start another import' }).click();
    await expect(page.getByRole('button', { name: /Download music/ })).toBeEnabled();
  });

  test('validates supported URLs before submitting', async ({ page }) => {
    let submitted = false;
    await page.route('**/api/admin/tracks/import', async (route) => {
      submitted = true;
      await route.fulfill({ status: 202, json: { jobId } });
    });

    await page.goto('/admin/tracks/import');
    await page.getByLabel('YouTube URL *').fill('https://example.com/video');
    await page.getByRole('button', { name: /Download music/ }).click();

    await expect(
      page.getByText('Enter a supported YouTube watch, short, or Shorts URL.'),
    ).toBeVisible();
    expect(submitted).toBe(false);
  });
});

test('normal users cannot enter or submit the import flow', async ({ page }) => {
  let submitted = false;
  await page.addInitScript(() => {
    localStorage.setItem('access_token', 'user-token');
    localStorage.setItem(
      'current_user',
      JSON.stringify({
        id: 'user-id',
        username: 'musicfan',
        email: 'musicfan@example.com',
        role: 'USER',
        avatarUrl: null,
      }),
    );
  });
  await page.route('**/api/admin/tracks/import', async (route) => {
    submitted = true;
    await route.fulfill({ status: 403 });
  });

  await page.goto('/admin/tracks/import');
  await expect(page).toHaveURL(/\/lobby/);
  expect(submitted).toBe(false);
});
