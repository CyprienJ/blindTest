import { expect, test } from '@playwright/test';

// Proves the isolated Compose stack (docker-compose.test.yml) is wired end to end: nginx
// serves the real Angular build, its /actuator/health proxy reaches a real backend that has
// itself already run Flyway against real PostgreSQL, and the e2e-only fake-email endpoint
// confirms the backend actually booted with SPRING_PROFILES_ACTIVE=e2e rather than silently
// falling back to a different profile.

test('backend health is reachable through the real nginx proxy', async ({ request, baseURL }) => {
  const response = await request.get(`${baseURL}/actuator/health`);

  expect(response.ok()).toBe(true);
  expect((await response.json())['status']).toBe('UP');
});

test('the e2e profile is active: the fake-email inbox answers and starts empty', async ({
  request,
  baseURL,
}) => {
  const response = await request.get(`${baseURL}/api/e2e/emails`, {
    params: { to: 'nobody@example.test' },
  });

  expect(response.ok()).toBe(true);
  expect(await response.json()).toEqual([]);
});

test('the frontend serves the real Angular build', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole('heading', { name: /Hear it\. Name it\./ })).toBeVisible();
});
