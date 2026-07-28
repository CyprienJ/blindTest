import { expect, test as base } from '@playwright/test';

export const test = base.extend({
  page: async ({ page }, use) => {
    await page.addInitScript(() => {
      type TurnstileOptions = {
        callback?: (token: string) => void;
        'expired-callback'?: () => void;
      };

      const state: {
        options?: TurnstileOptions;
        removeCount: number;
        resetCount: number;
      } = {
        removeCount: 0,
        resetCount: 0,
      };

      Object.defineProperty(window, '__turnstileMock', { value: state });
      Object.defineProperty(window, 'turnstile', {
        value: {
          render: (_container: string | HTMLElement, options: TurnstileOptions) => {
            state.options = options;
            return 'playwright-widget';
          },
          reset: () => {
            state.resetCount += 1;
          },
          remove: () => {
            state.removeCount += 1;
          },
        },
      });
    });

    await page.route(/^https?:\/\//, async (route) => {
      const url = new URL(route.request().url());
      const hostname = url.hostname;
      if (hostname === '127.0.0.1' || hostname === 'localhost') {
        if (url.pathname.startsWith('/api/')) {
          await route.fulfill({
            status: 501,
            contentType: 'application/json',
            body: JSON.stringify({ detail: `Unexpected API request: ${url.pathname}` }),
          });
        } else {
          await route.continue();
        }
      } else {
        await route.abort('blockedbyclient');
      }
    });

    await use(page);
  },
});

export { expect };
