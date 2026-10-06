import { Page, WebSocketRoute } from '@playwright/test';
import { expect, test } from './fixtures/test';
import { installSession, solveTurnstile, turnstileResetCount } from './support/auth';

// MP-09 and ADR-0022: guests join with a nickname, and the host chooses who hears the music.

const SESSION_ID = '77777777-7777-4777-8777-777777777777';
const HOST_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const GUEST_ID = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const CODE = 'XK9F2A';
const GUEST_TOKEN = 'guest-participant-token';
const publicDestination = `/topic/sessions/${SESSION_ID}`;

const connection = {
  protocolVersion: 1,
  webSocketEndpoint: '/ws',
  sockJsEndpoint: '/ws-sockjs',
  commandDestination: `/app/sessions/${SESSION_ID}`,
  eventDestination: publicDestination,
  privateDestination: `/user/queue/sessions/${SESSION_ID}`,
};

function member(participantId: string, displayName: string, ready = false, host = false) {
  return { participantId, displayName, ready, host, guest: !host };
}

function lobby(
  participantId: string,
  participants: ReturnType<typeof member>[],
  audioMode: 'EVERYONE' | 'HOST_ONLY' = 'HOST_ONLY',
) {
  return {
    sessionId: SESSION_ID,
    code: CODE,
    participantId,
    hostParticipantId: HOST_ID,
    participants,
    canStart: participants.every((entry) => entry.host || entry.ready),
    maxParticipants: 10,
    version: 1,
    expiresAt: '2026-10-02T13:00:00Z',
    connection,
    playlistName: 'Party mix',
    trackCount: 5,
    maxRoundSeconds: 30,
    audioMode,
  };
}

/** Minimal STOMP broker that records the CONNECT authorization and can push lobby events. */
async function mockStomp(page: Page) {
  let socket: WebSocketRoute | undefined;
  const subscriptions = new Map<string, string>();
  const authorizations: string[] = [];
  let eventNumber = 0;
  await page.routeWebSocket('**/ws', (ws) => {
    socket = ws;
    ws.onMessage((data) => {
      for (const frame of data.toString().split('\0')) {
        const [head] = frame.trimStart().split('\n\n');
        if (!head) continue;
        const [verb, ...lines] = head.split('\n');
        const headers = Object.fromEntries(
          lines.map((line) => [
            line.slice(0, line.indexOf(':')),
            line.slice(line.indexOf(':') + 1),
          ]),
        );
        if (verb === 'CONNECT' || verb === 'STOMP') {
          authorizations.push(headers['Authorization']);
          ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
        } else if (verb === 'SUBSCRIBE') {
          subscriptions.set(headers['destination'], headers['id']);
        }
      }
    });
  });
  return {
    authorizations,
    push: (type: string, payload: unknown) => {
      const body = JSON.stringify({
        type,
        payload,
        metadata: {
          protocolVersion: 1,
          eventId: `event-${++eventNumber}`,
          sessionId: SESSION_ID,
          sessionVersion: eventNumber,
          occurredAt: '2026-10-02T12:00:00Z',
        },
      });
      socket?.send(
        `MESSAGE\nsubscription:${subscriptions.get(publicDestination)}\nmessage-id:${eventNumber}\ncontent-type:application/json\n\n${body}\0`,
      );
    },
  };
}

async function noAccount(page: Page): Promise<void> {
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({ status: 401, json: { code: 'INVALID_REFRESH_TOKEN' } }),
  );
}

async function fillNickname(page: Page, nickname: string): Promise<void> {
  await page.getByLabel('Your nickname').fill(nickname);
}

test.describe('guest players', () => {
  test('a visitor joins from a shared link as a guest and follows the host into a silent game', async ({
    page,
  }) => {
    await noAccount(page);
    const stomp = await mockStomp(page);
    const joined = lobby(GUEST_ID, [member(HOST_ID, 'Host', false, true), member(GUEST_ID, 'Zoé')]);
    let joinBody: unknown;
    await page.route(`**/api/lobbies/${CODE}/guests`, (route) => {
      joinBody = route.request().postDataJSON();
      return route.fulfill({ json: { guestToken: GUEST_TOKEN, lobby: joined } });
    });
    const authorizations: (string | undefined)[] = [];
    await page.route(`**/api/lobbies/${CODE}`, (route) => {
      authorizations.push(route.request().headers()['authorization']);
      return route.fulfill({ json: joined });
    });
    await page.route(`**/api/lobbies/${CODE}/participants/me/ready`, (route) => {
      authorizations.push(route.request().headers()['authorization']);
      return route.fulfill({
        json: lobby(GUEST_ID, [
          member(HOST_ID, 'Host', false, true),
          member(GUEST_ID, 'Zoé', true),
        ]),
      });
    });

    await page.goto(`/lobby/join?code=${CODE}`);
    await expect(page.getByText("You're joining as a guest — no account required.")).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Choose a nickname' })).toBeDisabled();
    await fillNickname(page, '  Zoé ');
    await expect(page.getByRole('button', { name: 'Complete the security check' })).toBeDisabled();
    await solveTurnstile(page, 'guest-turnstile-token');
    await page.getByRole('button', { name: 'Join lobby →' }).click();

    await expect(page).toHaveURL(new RegExp(`/lobby/${CODE}$`));
    expect(joinBody).toEqual({ nickname: 'Zoé', turnstileToken: 'guest-turnstile-token' });
    await expect(page.getByText('Guest', { exact: true })).toBeVisible();
    await expect(
      page.getByText("The music plays on the host's device only — stay within earshot."),
    ).toBeVisible();
    await page.getByRole('button', { name: "I'm ready" }).click();
    await expect(page.getByRole('button', { name: 'Not ready' })).toBeVisible();
    expect(authorizations.every((value) => value === `Bearer ${GUEST_TOKEN}`)).toBe(true);
    await expect.poll(() => stomp.authorizations).toContain(`Bearer ${GUEST_TOKEN}`);

    // Reloading the tab keeps the guest in its lobby.
    await page.reload();
    await expect(page).toHaveURL(new RegExp(`/lobby/${CODE}$`));
    await expect(page.getByLabel('Session code')).toHaveText(CODE);

    stomp.push('LOBBY_STARTED', { totalRounds: 5 });
    await expect(page).toHaveURL(new RegExp(`/game/${SESSION_ID}$`));
    await expect(page.getByText('Multiplayer blindtest')).toBeVisible();
  });

  test('a new tab cannot reuse another tab’s guest seat', async ({ page, context }) => {
    await noAccount(page);
    await page.goto('/');
    await page.evaluate(
      ({ code, sessionId, participantId }) =>
        sessionStorage.setItem(
          'guest_session',
          JSON.stringify({ token: 't', code, sessionId, participantId, nickname: 'Zoé' }),
        ),
      { code: CODE, sessionId: SESSION_ID, participantId: GUEST_ID },
    );

    const other = await context.newPage();
    await other.route('**/api/auth/refresh', (route) =>
      route.fulfill({ status: 401, json: { code: 'INVALID_REFRESH_TOKEN' } }),
    );
    await other.goto(`/lobby/${CODE}`);
    await expect(other).toHaveURL(/\/auth\/login/);
  });

  for (const [code, status, message] of [
    ['GUEST_NICKNAME_TAKEN', 409, 'Someone in this lobby already uses this nickname.'],
    ['GUEST_JOIN_RATE_LIMITED', 429, 'Too many attempts to join.'],
    ['TURNSTILE_FAILED', 403, 'Security check failed. Please try again.'],
    ['LOBBY_NOT_FOUND', 404, 'No open lobby uses this code.'],
  ] as const) {
    test(`explains a ${code} guest join failure and asks for a new check`, async ({ page }) => {
      await noAccount(page);
      await page.route(`**/api/lobbies/${CODE}/guests`, (route) =>
        route.fulfill({ status, json: { code } }),
      );
      await page.goto(`/lobby/join?code=${CODE}`);
      await fillNickname(page, 'Zoé');
      await solveTurnstile(page);
      await page.getByRole('button', { name: 'Join lobby →' }).click();

      await expect(page.getByRole('alert')).toContainText(message);
      await expect(page).toHaveURL(/\/lobby\/join/);
      await expect.poll(() => turnstileResetCount(page)).toBe(1);
      await expect(
        page.getByRole('button', { name: 'Complete the security check' }),
      ).toBeDisabled();
    });
  }
});

test.describe('host audio choice', () => {
  test.beforeEach(async ({ page }) => {
    await installSession(page);
  });

  test('the host switches the music to its own device from the lobby', async ({ page }) => {
    await mockStomp(page);
    await page.route(`**/api/lobbies/${CODE}`, (route) =>
      route.fulfill({
        json: lobby(HOST_ID, [member(HOST_ID, 'musicfan', false, true)], 'EVERYONE'),
      }),
    );
    let audioBody: unknown;
    await page.route(`**/api/lobbies/${CODE}/audio-mode`, (route) => {
      audioBody = route.request().postDataJSON();
      return route.fulfill({
        json: lobby(HOST_ID, [member(HOST_ID, 'musicfan', false, true)], 'HOST_ONLY'),
      });
    });

    await page.goto(`/lobby/${CODE}`);
    await expect(page.getByRole('radio', { name: 'Every player hears the music' })).toBeChecked();
    await page.getByRole('radio', { name: 'Only my device plays it' }).click();

    expect(audioBody).toEqual({ audioMode: 'HOST_ONLY' });
    await expect(page.getByRole('radio', { name: 'Only my device plays it' })).toBeChecked();
    await expect(page.getByText("Host's device only")).toBeVisible();
  });
});
