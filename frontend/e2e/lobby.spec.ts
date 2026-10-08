import { Page, WebSocketRoute } from '@playwright/test';
import { expect, test } from './fixtures/test';
import { installSession } from './support/auth';

// MP-05 and MP-11: create, join, ready, kick, start, change the settings of, and return to a
// multiplayer lobby against mocked REST and STOMP.

const SESSION_ID = '77777777-7777-4777-8777-777777777777';
const HOST_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const FRIEND_ID = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const OTHER_ID = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const CODE = 'XK9F2A';
const PLAYLIST_ID = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
const publicDestination = `/topic/sessions/${SESSION_ID}`;

const connection = {
  protocolVersion: 1,
  webSocketEndpoint: '/ws',
  sockJsEndpoint: '/ws-sockjs',
  commandDestination: `/app/sessions/${SESSION_ID}`,
  eventDestination: publicDestination,
  privateDestination: `/user/queue/sessions/${SESSION_ID}`,
};

type Member = { participantId: string; displayName: string; ready: boolean; host: boolean };

function member(participantId: string, displayName: string, ready = false, host = false) {
  return { participantId, displayName, ready, host, guest: false };
}

function lobby(participantId: string, participants: Member[], canStart = false) {
  return {
    sessionId: SESSION_ID,
    code: CODE,
    participantId,
    hostParticipantId: HOST_ID,
    participants: participants.map((entry) => ({ ...entry, guest: false })),
    canStart,
    maxParticipants: 10,
    version: 1,
    expiresAt: '2026-10-02T13:00:00Z',
    connection,
    playlistId: PLAYLIST_ID,
    playlistName: 'Party mix',
    trackCount: 5,
    maxRoundSeconds: 30,
    difficultyLevels: null,
    includeUnrated: true,
    audioMode: 'EVERYONE',
  };
}

const settings = {
  audioMode: 'EVERYONE',
  playlistName: 'Party mix',
  trackCount: 5,
  maxRoundSeconds: 30,
};

/** Minimal STOMP broker: acknowledges CONNECT and SUBSCRIBE and lets tests push lobby events. */
async function mockStomp(page: Page): Promise<{ push: (type: string, payload: unknown) => void }> {
  let socket: WebSocketRoute | undefined;
  const subscriptions = new Map<string, string>();
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
          ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
        } else if (verb === 'SUBSCRIBE') {
          subscriptions.set(headers['destination'], headers['id']);
        }
      }
    });
  });
  return {
    push: (type, payload) => {
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

async function mockCatalogue(page: Page): Promise<void> {
  const playlist = {
    id: PLAYLIST_ID,
    name: 'Party mix',
    description: 'Crowd pleasers',
    imageUrl: null,
    trackCount: 8,
  };
  await page.route('**/api/playlists', (route) => route.fulfill({ json: [playlist] }));
  await page.route('**/api/playlists/*', (route) =>
    route.fulfill({
      json: {
        ...playlist,
        tracks: Array.from({ length: 8 }, (_, index) => ({
          id: `track-${index}`,
          title: `Track ${index}`,
          artist: 'Artist',
          album: null,
          composer: null,
          work: null,
          category: 'Pop',
          decade: 1980,
          durationSeconds: 180,
          excerptStartSeconds: 0,
          imageUrl: null,
          difficulty: null,
        })),
      },
    }),
  );
}

test.describe('multiplayer lobby', () => {
  test.beforeEach(async ({ page }) => {
    await installSession(page);
  });

  test('host opens a lobby, sees a friend get ready, and starts the game', async ({ page }) => {
    await mockCatalogue(page);
    const stomp = await mockStomp(page);
    let created: unknown;
    await page.route('**/api/lobbies', (route) => {
      created = route.request().postDataJSON();
      return route.fulfill({
        status: 201,
        json: lobby(HOST_ID, [member(HOST_ID, 'musicfan', false, true)]),
      });
    });
    let reads = 0;
    await page.route(`**/api/lobbies/${CODE}`, (route) => {
      reads++;
      return route.fulfill({ json: lobby(HOST_ID, [member(HOST_ID, 'musicfan', false, true)]) });
    });
    let started = false;
    await page.route(`**/api/lobbies/${CODE}/start`, (route) => {
      started = true;
      return route.fulfill({
        json: { sessionId: SESSION_ID, participantId: HOST_ID, state: 'CREATED', version: 0 },
      });
    });

    await page.goto('/lobby/create');
    await page.getByRole('radio', { name: 'With friends' }).click();
    await page.getByRole('radio', { name: /Only my device/ }).check();
    await page.getByRole('button', { name: 'Open lobby →' }).click();

    await expect(page).toHaveURL(new RegExp(`/lobby/${CODE}$`));
    expect(created).toMatchObject({
      playlistId: PLAYLIST_ID,
      trackCount: 8,
      audioMode: 'HOST_ONLY',
    });
    await expect(page.getByLabel('Session code')).toHaveText(CODE);
    await expect(page.getByText('Party mix')).toBeVisible();
    const start = page.getByRole('button', { name: '▶ Start game' });
    await expect(start).toBeDisabled();
    await expect(page.getByText('Waiting for at least one more player.')).toBeVisible();
    // The lobby is re-read once the live topic is subscribed.
    await expect.poll(() => reads).toBeGreaterThanOrEqual(2);

    stomp.push('LOBBY_UPDATED', {
      code: CODE,
      hostParticipantId: HOST_ID,
      participants: [member(HOST_ID, 'musicfan', false, true), member(FRIEND_ID, 'Léa')],
      canStart: false,
      ...settings,
    });
    await expect(page.getByText('Léa')).toBeVisible();
    await expect(page.getByText('Registered · Not ready')).toBeVisible();
    await expect(start).toBeDisabled();

    stomp.push('LOBBY_UPDATED', {
      code: CODE,
      hostParticipantId: HOST_ID,
      participants: [member(HOST_ID, 'musicfan', false, true), member(FRIEND_ID, 'Léa', true)],
      canStart: true,
      ...settings,
    });
    await expect(page.getByText('2/2 ready')).toBeVisible();
    await start.click();

    await expect(page).toHaveURL(new RegExp(`/game/${SESSION_ID}$`));
    expect(started).toBe(true);
  });

  test('host removes a player', async ({ page }) => {
    await mockStomp(page);
    const players = [
      member(HOST_ID, 'musicfan', false, true),
      member(FRIEND_ID, 'Léa', true),
      member(OTHER_ID, 'Max'),
    ];
    await page.route(`**/api/lobbies/${CODE}`, (route) =>
      route.fulfill({ json: lobby(HOST_ID, players) }),
    );
    let removed = '';
    await page.route(`**/api/lobbies/${CODE}/participants/*`, (route) => {
      removed = route.request().url().split('/').at(-1) ?? '';
      return route.fulfill({ status: 204 });
    });

    await page.goto(`/lobby/${CODE}`);
    await page.getByRole('button', { name: 'Remove Max' }).click();

    await expect(page.getByText('Max')).toHaveCount(0);
    expect(removed).toBe(OTHER_ID);
  });

  test('a friend joins by code, gets ready, and follows the host into the game', async ({
    page,
  }) => {
    const stomp = await mockStomp(page);
    const joined = lobby(FRIEND_ID, [
      member(HOST_ID, 'Host', false, true),
      member(FRIEND_ID, 'musicfan'),
    ]);
    await page.route(`**/api/lobbies/${CODE}/participants`, (route) =>
      route.fulfill({ json: joined }),
    );
    await page.route(`**/api/lobbies/${CODE}`, (route) => route.fulfill({ json: joined }));
    let readyBody: unknown;
    await page.route(`**/api/lobbies/${CODE}/participants/me/ready`, (route) => {
      readyBody = route.request().postDataJSON();
      return route.fulfill({
        json: lobby(
          FRIEND_ID,
          [member(HOST_ID, 'Host', false, true), member(FRIEND_ID, 'musicfan', true)],
          true,
        ),
      });
    });

    await page.goto('/lobby/join');
    const join = page.getByRole('button', { name: /Enter 6 more characters/ });
    await expect(join).toBeDisabled();
    await page.getByLabel('Code character 1 of 6').pressSequentially(CODE.toLowerCase());
    await page.getByRole('button', { name: 'Join lobby →' }).click();

    await expect(page).toHaveURL(new RegExp(`/lobby/${CODE}$`));
    await expect(page.getByText('The host starts the game once everyone is ready.')).toBeVisible();
    await page.getByRole('button', { name: "I'm ready" }).click();
    expect(readyBody).toEqual({ ready: true });
    await expect(page.getByRole('button', { name: 'Not ready' })).toBeVisible();

    stomp.push('LOBBY_STARTED', { totalRounds: 5 });
    await expect(page).toHaveURL(new RegExp(`/game/${SESSION_ID}$`));
    await expect(page.getByText('Multiplayer blindtest')).toBeVisible();
  });

  test('host changes the playlist rules from the lobby', async ({ page }) => {
    await mockCatalogue(page);
    await mockStomp(page);
    const players = [member(HOST_ID, 'musicfan', false, true), member(FRIEND_ID, 'Léa', true)];
    await page.route(`**/api/lobbies/${CODE}`, (route) =>
      route.fulfill({ json: lobby(HOST_ID, players, true) }),
    );
    let saved: unknown;
    await page.route(`**/api/lobbies/${CODE}/settings`, (route) => {
      saved = route.request().postDataJSON();
      return route.fulfill({
        json: {
          ...lobby(HOST_ID, [players[0], member(FRIEND_ID, 'Léa')]),
          trackCount: 8,
          audioMode: 'HOST_ONLY',
        },
      });
    });

    await page.goto(`/lobby/${CODE}`);
    await page.getByRole('link', { name: 'Change settings' }).click();
    await expect(page).toHaveURL(new RegExp(`/lobby/${CODE}/settings$`));
    await expect(page.getByRole('heading', { name: 'Lobby settings' })).toBeVisible();
    await expect(page.getByRole('radio', { name: 'With friends' })).toHaveCount(0);
    await page.getByRole('radio', { name: /Only my device/ }).check();
    await page.getByRole('button', { name: 'Save settings' }).click();

    await expect(page).toHaveURL(new RegExp(`/lobby/${CODE}$`));
    expect(saved).toMatchObject({
      playlistId: PLAYLIST_ID,
      trackCount: 5,
      maxRoundSeconds: 30,
      audioMode: 'HOST_ONLY',
    });
  });

  test('a friend sees the host change the rules and must get ready again', async ({ page }) => {
    const stomp = await mockStomp(page);
    const players = [member(HOST_ID, 'Host', false, true), member(FRIEND_ID, 'musicfan', true)];
    await page.route(`**/api/lobbies/${CODE}`, (route) =>
      route.fulfill({ json: lobby(FRIEND_ID, players, true) }),
    );

    await page.goto(`/lobby/${CODE}`);
    await expect(page.getByRole('button', { name: 'Not ready' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Change settings' })).toHaveCount(0);

    stomp.push('LOBBY_UPDATED', {
      code: CODE,
      hostParticipantId: HOST_ID,
      participants: [member(HOST_ID, 'Host', false, true), member(FRIEND_ID, 'musicfan')],
      canStart: false,
      ...settings,
      playlistName: 'Rock classics',
      maxRoundSeconds: 45,
    });
    await expect(page.getByText('Rock classics')).toBeVisible();
    await expect(page.getByText('45s')).toBeVisible();
    await expect(page.getByRole('button', { name: "I'm ready" })).toBeVisible();
  });

  test('players go back to the reopened lobby after a game', async ({ page }) => {
    await mockStomp(page);
    const playedId = '99999999-9999-4999-8999-999999999999';
    await page.route(`**/api/sessions/${playedId}/summary`, (route) =>
      route.fulfill({
        json: {
          sessionId: playedId,
          totalScore: 300,
          totalRounds: 5,
          titlesFound: 3,
          skippedRounds: 1,
          timedOutRounds: 1,
          startedAt: '2026-10-02T12:00:00.000Z',
          completedAt: '2026-10-02T12:04:00.000Z',
          rounds: [],
          gameMode: 'MULTIPLAYER',
          participantId: FRIEND_ID,
          standings: [
            {
              participantId: FRIEND_ID,
              displayName: 'musicfan',
              guest: false,
              rank: 1,
              finalScore: 300,
            },
          ],
        },
      }),
    );
    const reopened = lobby(FRIEND_ID, [member(FRIEND_ID, 'musicfan', false, true)]);
    let rematches = 0;
    await page.route(`**/api/sessions/${playedId}/rematch`, (route) => {
      rematches++;
      return route.fulfill({
        json: { guestToken: null, lobby: { ...reopened, hostParticipantId: FRIEND_ID } },
      });
    });
    await page.route(`**/api/lobbies/${CODE}`, (route) =>
      route.fulfill({ json: { ...reopened, hostParticipantId: FRIEND_ID } }),
    );

    await page.goto(`/end-screen/${playedId}`);
    await expect(page.getByRole('button', { name: 'New game' })).toBeVisible();
    await page.getByRole('button', { name: 'Back to lobby' }).click();

    await expect(page).toHaveURL(new RegExp(`/lobby/${CODE}$`));
    expect(rematches).toBe(1);
    await expect(page.getByLabel('Session code')).toHaveText(CODE);
    await expect(page.getByRole('link', { name: 'Change settings' })).toBeVisible();
  });

  for (const [code, status, message] of [
    ['LOBBY_NOT_FOUND', 404, 'No open lobby uses this code.'],
    ['LOBBY_FULL', 409, 'This lobby is full (10 players).'],
    ['EMAIL_NOT_VERIFIED', 403, 'Verify your email address before joining a game.'],
  ] as const) {
    test(`explains a ${code} join failure`, async ({ page }) => {
      await page.route(`**/api/lobbies/${CODE}/participants`, (route) =>
        route.fulfill({ status, json: { code } }),
      );
      await page.goto(`/lobby/join?code=${CODE}`);
      await page.getByRole('button', { name: 'Join lobby →' }).click();
      await expect(page.getByRole('alert')).toContainText(message);
      await expect(page).toHaveURL(/\/lobby\/join/);
    });
  }

  test('rejects characters that are never part of a code', async ({ page }) => {
    await page.goto('/lobby/join');
    await page.getByLabel('Code character 1 of 6').pressSequentially('0');
    await expect(page.getByRole('alert')).toContainText('letters and the digits 2 to 9 only');
    await expect(page.getByLabel('Code character 1 of 6')).toHaveValue('');
  });
});
