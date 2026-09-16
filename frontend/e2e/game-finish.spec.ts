import { expect, test } from './fixtures/test';
import { installSession, mockJson } from './support/auth';

const sessionId = '55555555-5555-4555-8555-555555555555';
const participantId = '66666666-6666-4666-8666-666666666666';
const publicDestination = `/topic/sessions/${sessionId}`;
const privateDestination = `/user/queue/sessions/${sessionId}`;
const now = new Date('2026-09-11T12:00:00Z');

test('finishes the last round and links through to the persisted summary', async ({ page }) => {
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
          playlistName: 'Finish test',
          trackCount: 1,
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
    { sessionId, participantId, publicDestination, privateDestination },
  );

  let advanceCount = 0;
  await page.routeWebSocket('**/ws', (ws) => {
    const subscriptions = new Map<string, string>();
    let pendingSnapshot = false;
    let eventNumber = 0;
    const emit = (destination: string, type: string, payload: unknown) => {
      const body = JSON.stringify({
        type,
        payload,
        commandId: null,
        recipientParticipantId: participantId,
        metadata: {
          protocolVersion: 1,
          eventId: `event-${++eventNumber}`,
          sessionId,
          sessionVersion: eventNumber,
          occurredAt: now.toISOString(),
        },
      });
      ws.send(
        `MESSAGE\nsubscription:${subscriptions.get(destination)}\nmessage-id:${eventNumber}\ncontent-type:application/json\n\n${body}\0`,
      );
    };
    const snapshot = () => {
      if (!pendingSnapshot || !subscriptions.has(privateDestination)) return;
      pendingSnapshot = false;
      emit(privateDestination, 'CURRENT_SNAPSHOT', {
        gameStatus: 'PLAYING',
        currentRoundIndex: 0,
        totalRounds: 1,
        totalScore: 500,
        discoveredAnswerTypes: [],
        expiresAt: '2026-09-11T13:00:00Z',
        currentRound: {
          roundIndex: 0,
          status: 'REVEALED',
          startsAt: now.toISOString(),
          deadline: new Date(now.getTime() + 30000).toISOString(),
          playbackUrl: null,
          excerptStartSeconds: 0,
          outcome: 'TITLE_FOUND',
          title: 'Last Track',
          artist: 'Solo Artist',
          composer: null,
          album: null,
          advanceAt: new Date(now.getTime() + 1000).toISOString(),
          roundPoints: 500,
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
          } else if (command.type === 'ADVANCE_ROUND') {
            advanceCount++;
            emit(publicDestination, 'GAME_FINISHED', {
              totalScore: 500,
              totalRounds: 1,
              titlesFound: 1,
              skippedRounds: 0,
              timedOutRounds: 0,
            });
          }
        }
      }
    });
  });

  await page.goto(`/game/${sessionId}`);

  await expect(page.getByRole('heading', { name: 'Last Track' })).toBeVisible();
  const finishButton = page.getByRole('button', { name: 'Finish now', exact: true });
  await expect(finishButton).toBeEnabled();
  await finishButton.click();
  expect(advanceCount).toBe(1);

  await expect(page.getByRole('heading', { name: "That's a wrap." })).toBeVisible();
  await expect(page.locator('.final-score')).toHaveText('500');
  await expect(page.getByText('1 found · 0 skipped · 0 timed out')).toBeVisible();

  const summary = {
    sessionId,
    totalScore: 500,
    totalRounds: 1,
    titlesFound: 1,
    skippedRounds: 0,
    timedOutRounds: 0,
    startedAt: '2026-09-11T12:00:00.000Z',
    completedAt: '2026-09-11T12:00:20.000Z',
    rounds: [
      {
        roundIndex: 0,
        outcome: 'TITLE_FOUND',
        points: 500,
        title: 'Last Track',
        artist: 'Solo Artist',
        composer: null,
        album: null,
        startedAt: '2026-09-11T12:00:00.000Z',
        revealedAt: '2026-09-11T12:00:05.000Z',
        completedAt: '2026-09-11T12:00:20.000Z',
      },
    ],
  };
  await mockJson(page, `/api/sessions/${sessionId}/summary`, 200, summary);

  await page.getByRole('link', { name: 'View summary' }).click();

  await expect(page).toHaveURL(new RegExp(`/end-screen/${sessionId}$`));
  await expect(page.getByRole('heading', { name: "That's a wrap." })).toBeVisible();
  await expect(page.locator('.rounds-list li')).toHaveCount(1);
  await expect(page.locator('.rounds-list li')).toContainText('Last Track');
});
