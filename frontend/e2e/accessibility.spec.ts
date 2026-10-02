import { expect, Locator, Page, test } from '@playwright/test';
import { installSession, mockJson } from './support/auth';

const SESSION_ID = '55555555-5555-4555-8555-555555555555';
const PARTICIPANT_ID = '66666666-6666-4666-8666-666666666666';
const PUBLIC_DESTINATION = `/topic/sessions/${SESSION_ID}`;
const PRIVATE_DESTINATION = `/user/queue/sessions/${SESSION_ID}`;

/**
 * Firefox briefly focuses the document body on the first Tab press before
 * handing focus to a real element; skip past that so all three browsers walk
 * the same sequence of interactive elements.
 */
async function tabToNextControl(page: Page): Promise<Locator> {
  await page.keyboard.press('Tab');
  if (await page.evaluate(() => document.activeElement === document.body)) {
    await page.keyboard.press('Tab');
  }
  return page.locator(':focus');
}

async function expectVisibleFocusRing(locator: Locator): Promise<void> {
  const hasVisibleRing = await locator.evaluate((element) => {
    const style = getComputedStyle(element);
    const hasOutline = style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0;
    const hasShadow = style.boxShadow !== 'none' && style.boxShadow.trim() !== '';
    return hasOutline || hasShadow;
  });
  expect(hasVisibleRing).toBe(true);
}

async function focusedIdentity(locator: Locator): Promise<string> {
  return locator.evaluate((element) => {
    const el = element as HTMLInputElement;
    const label = el.id ? document.querySelector(`label[for="${el.id}"]`) : null;
    const name =
      el.getAttribute('aria-label') ||
      label?.textContent ||
      el.getAttribute('placeholder') ||
      el.textContent ||
      el.tagName;
    return name.trim();
  });
}

test.describe('keyboard and visible focus', () => {
  test('reaches every sign-in control by keyboard with a visible focus ring', async ({ page }) => {
    await page.goto('/auth/login');
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();

    const expectedControls = [
      'Email',
      'Password',
      'Show password',
      'Sign in',
      'Forgot your password?',
    ];
    const visited = new Set<string>();

    for (let step = 0; step < 10; step++) {
      const focused = await tabToNextControl(page);
      await expectVisibleFocusRing(focused);
      visited.add(await focusedIdentity(focused));
    }

    for (const control of expectedControls) {
      const reached = [...visited].some((identity) => identity.includes(control));
      expect(reached, `expected keyboard focus to reach "${control}", saw ${[...visited]}`).toBe(
        true,
      );
    }
  });

  test('reaches the primary calls to action on the public home page by keyboard', async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: 'Sign in to play' })).toBeVisible();

    const expectedControls = ['Sign in', 'Sign in to play'];
    const visited = new Set<string>();

    for (let step = 0; step < 10; step++) {
      const focused = await tabToNextControl(page);
      await expectVisibleFocusRing(focused);
      visited.add(await focusedIdentity(focused));
    }

    for (const control of expectedControls) {
      const reached = [...visited].some((identity) => identity.includes(control));
      expect(reached, `expected keyboard focus to reach "${control}", saw ${[...visited]}`).toBe(
        true,
      );
    }
  });

  test('keeps focus on the answer field while a submitted answer is judged', async ({ page }) => {
    await installSession(page);
    await page.addInitScript(
      ({ sessionId, participantId, publicDestination, privateDestination }) => {
        sessionStorage.setItem(
          'active_solo_session',
          JSON.stringify({
            sessionId,
            participantId,
            state: 'PLAYING',
            version: 1,
            expiresAt: '2026-09-11T13:00:00Z',
            playlistName: 'Focus test',
            trackCount: 3,
            maxRoundSeconds: 30,
            connection: {
              protocolVersion: 1,
              webSocketEndpoint: '/ws',
              sockJsEndpoint: '/ws-sockjs',
              commandDestination: `/app/sessions/${sessionId}`,
              eventDestination: publicDestination,
              privateDestination,
            },
          }),
        );
      },
      {
        sessionId: SESSION_ID,
        participantId: PARTICIPANT_ID,
        publicDestination: PUBLIC_DESTINATION,
        privateDestination: PRIVATE_DESTINATION,
      },
    );

    await page.routeWebSocket('**/ws', (ws) => {
      const subscriptions = new Map<string, string>();
      let pendingSnapshot = false;
      let eventNumber = 0;
      const emit = (destination: string, type: string, payload: unknown) => {
        const body = JSON.stringify({
          type,
          payload,
          commandId: null,
          recipientParticipantId: PARTICIPANT_ID,
          metadata: {
            protocolVersion: 1,
            eventId: `event-${++eventNumber}`,
            sessionId: SESSION_ID,
            sessionVersion: eventNumber,
            occurredAt: new Date().toISOString(),
          },
        });
        ws.send(
          `MESSAGE\nsubscription:${subscriptions.get(destination)}\nmessage-id:${eventNumber}\ncontent-type:application/json\n\n${body}\0`,
        );
      };
      const snapshot = () => {
        if (!pendingSnapshot || !subscriptions.has(PRIVATE_DESTINATION)) return;
        pendingSnapshot = false;
        emit(PRIVATE_DESTINATION, 'CURRENT_SNAPSHOT', {
          gameStatus: 'PLAYING',
          currentRoundIndex: 0,
          totalRounds: 3,
          totalScore: 0,
          discoveredAnswerTypes: [],
          expiresAt: '2026-09-11T13:00:00Z',
          currentRound: {
            roundIndex: 0,
            status: 'PLAYING',
            startsAt: new Date().toISOString(),
            deadline: new Date(Date.now() + 30000).toISOString(),
            playbackUrl: null,
            excerptStartSeconds: 0,
            excerptDurationSeconds: 60,
            outcome: null,
            title: null,
            artist: null,
            composer: null,
            album: null,
            advanceAt: null,
            roundPoints: 0,
          },
        });
      };
      ws.onMessage((data) => {
        for (const frame of data.toString().split('\0')) {
          const [head, body] = frame.trimStart().split('\n\n');
          if (!head) continue;
          const [verb, ...lines] = head.split('\n');
          const headers = Object.fromEntries(
            lines.map((line) => {
              const colon = line.indexOf(':');
              return [line.slice(0, colon), line.slice(colon + 1)];
            }),
          );
          if (verb === 'CONNECT' || verb === 'STOMP') {
            ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
          } else if (verb === 'SUBSCRIBE') {
            subscriptions.set(headers['destination'], headers['id']);
            snapshot();
          } else if (verb === 'SEND') {
            const command = JSON.parse(body);
            if (command.type === 'REQUEST_SNAPSHOT') {
              pendingSnapshot = true;
              snapshot();
            } else if (command.type === 'SUBMIT_ANSWER') {
              emit(PRIVATE_DESTINATION, 'ANSWER_REJECTED', {
                reason: 'WRONG_ANSWER',
                retryAt: null,
              });
            }
          }
        }
      });
    });

    await page.goto(`/game/${SESSION_ID}`);
    const input = page.getByRole('textbox', { name: 'Your answer' });
    const feedback = page.locator('#answer-feedback');

    await expect(feedback).toHaveAttribute('role', 'status');
    await expect(feedback).toHaveAttribute('aria-atomic', 'true');
    await expect(input).toHaveAttribute('aria-describedby', 'answer-feedback');

    await input.fill('wrong guess');
    await input.press('Enter');

    await expect(feedback).toContainText('Not a match');
    await expect(input).toBeFocused();
    await expectVisibleFocusRing(input);
  });
});

test.describe('small screens do not crash', () => {
  const viewport = { width: 360, height: 740 };

  test('core screens render without a page error on a small viewport', async ({ page }) => {
    await page.setViewportSize(viewport);
    const pageErrors: Error[] = [];
    page.on('pageerror', (error) => pageErrors.push(error));

    await page.goto('/');
    await expect(page.getByRole('heading', { name: /Hear it\. Name it\./ })).toBeVisible();

    await page.goto('/auth/login');
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();

    await page.goto('/auth/register');
    await expect(page.locator('.bt-card')).toBeVisible();

    await installSession(page);
    await mockJson(page, '/api/playlists', 200, []);

    await page.goto('/home');
    await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toBeVisible();

    await page.goto('/lobby/create');
    await expect(page.getByRole('heading', { name: 'Configure a new game' })).toBeVisible();

    expect(pageErrors, pageErrors.map(String).join('\n')).toHaveLength(0);
  });
});
