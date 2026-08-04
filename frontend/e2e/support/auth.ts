import { expect, Page } from '@playwright/test';

export const testUser = {
  id: '11111111-1111-4111-8111-111111111111',
  username: 'musicfan',
  email: 'musicfan@example.com',
  role: 'USER',
  avatarUrl: null,
};

export async function mockJson(
  page: Page,
  path: string,
  status: number,
  body: unknown = {},
): Promise<void> {
  await page.route(`**${path}`, async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  });
}

export async function installSession(page: Page, user = testUser): Promise<void> {
  await page.addInitScript(
    ({ user }) => {
      localStorage.setItem('access_token', 'existing-access-token');
      localStorage.setItem('current_user', JSON.stringify(user));
    },
    { user },
  );
}

export async function solveTurnstile(page: Page, token = 'turnstile-token'): Promise<void> {
  await expect
    .poll(() =>
      page.evaluate(() => {
        const mock = window as unknown as {
          __turnstileMock: { options?: { callback?: (value: string) => void } };
        };
        return Boolean(mock.__turnstileMock.options?.callback);
      }),
    )
    .toBe(true);

  await page.evaluate((value) => {
    const mock = window as unknown as {
      __turnstileMock: { options?: { callback?: (token: string) => void } };
    };
    mock.__turnstileMock.options?.callback?.(value);
  }, token);
}

export async function expireTurnstile(page: Page): Promise<void> {
  await page.evaluate(() => {
    const mock = window as unknown as {
      __turnstileMock: { options?: { 'expired-callback'?: () => void } };
    };
    mock.__turnstileMock.options?.['expired-callback']?.();
  });
}

export async function failTurnstile(page: Page): Promise<boolean | undefined> {
  return page.evaluate(() => {
    const mock = window as unknown as {
      __turnstileMock: { options?: { 'error-callback'?: () => boolean } };
    };
    return mock.__turnstileMock.options?.['error-callback']?.();
  });
}

export async function turnstileResetCount(page: Page): Promise<number> {
  return page.evaluate(() => {
    const mock = window as unknown as { __turnstileMock: { resetCount: number } };
    return mock.__turnstileMock.resetCount;
  });
}
