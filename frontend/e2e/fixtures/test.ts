import { expect, test as base } from '@playwright/test';

export const test = base.extend({
  page: async ({ page }, use) => {
    await page.route(/^https?:\/\//, async (route) => {
      const hostname = new URL(route.request().url()).hostname;
      if (hostname === '127.0.0.1' || hostname === 'localhost') {
        await route.continue();
      } else {
        await route.abort('blockedbyclient');
      }
    });

    await use(page);
  },
});

export { expect };
