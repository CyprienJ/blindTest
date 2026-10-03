import { expect, test } from './fixtures/test';
import { installSession } from './support/auth';

const sessionId = '55555555-5555-4555-8555-555555555555';
const participantId = '66666666-6666-4666-8666-666666666666';
const publicDestination = `/topic/sessions/${sessionId}`;
const privateDestination = `/user/queue/sessions/${sessionId}`;
const now = new Date('2026-09-11T12:00:00Z');
const at = (seconds: number) => new Date(now.getTime() + seconds * 1000).toISOString();

test('pauses a solo round and its reveal, then resumes both', async ({ page }) => {
  await installSession(page);
  await page.clock.install({ time: now });
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
          playlistName: 'Pause test',
          trackCount: 5,
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

  // Server time in seconds after `now`, advanced by the test alongside the page clock.
  let serverSeconds = 0;
  let phase: 'PLAYING' | 'REVEALED' = 'PLAYING';
  const commands: string[] = [];
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
          occurredAt: at(serverSeconds),
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
        totalRounds: 5,
        totalScore: 0,
        discoveredAnswerTypes: [],
        expiresAt: '2026-09-11T13:00:00Z',
        currentRound: {
          roundIndex: 0,
          status: 'PLAYING',
          startsAt: at(0),
          deadline: at(30),
          playbackUrl: null,
          excerptStartSeconds: 0,
          outcome: null,
          title: null,
          artist: null,
          composer: null,
          album: null,
          streamingLinks: {},
          coverUrl: null,
          advanceAt: null,
          pausedAt: null,
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
          commands.push(command.type);
          if (command.type === 'REQUEST_SNAPSHOT') {
            pendingSnapshot = true;
            snapshot();
          } else if (command.type === 'PAUSE_ROUND') {
            emit(publicDestination, 'ROUND_PAUSED', {
              roundIndex: 0,
              totalRounds: 5,
              status: phase,
              pausedAt: at(serverSeconds),
            });
          } else if (command.type === 'RESUME_ROUND' && phase === 'PLAYING') {
            // Paused at 10 s and resumed at 70 s: the round moves by 60 s.
            emit(publicDestination, 'ROUND_RESUMED', {
              roundIndex: 0,
              totalRounds: 5,
              status: 'PLAYING',
              startsAt: at(60),
              deadline: at(90),
              excerptStartSeconds: 0,
              excerptDurationSeconds: 30,
              playbackUrl: null,
              transitionAt: null,
            });
          } else if (command.type === 'RESUME_ROUND') {
            emit(publicDestination, 'ROUND_RESUMED', {
              roundIndex: 0,
              totalRounds: 5,
              status: 'REVEALED',
              startsAt: null,
              deadline: null,
              excerptStartSeconds: null,
              excerptDurationSeconds: null,
              playbackUrl: null,
              transitionAt: at(serverSeconds + 30),
            });
          } else if (command.type === 'SUBMIT_ANSWER') {
            phase = 'REVEALED';
            emit(publicDestination, 'ROUND_REVEALED', {
              roundIndex: 0,
              totalRounds: 5,
              outcome: 'TITLE_FOUND',
              title: 'Secret title',
              artist: 'Known artist',
              composer: null,
              album: null,
              streamingLinks: {},
              coverUrl: null,
              roundPoints: 500,
              totalScore: 500,
              transitionAt: at(serverSeconds + 4),
            });
          }
        }
      }
    });
  });

  await page.goto(`/game/${sessionId}`);
  const input = page.getByRole('textbox', { name: 'Your answer' });
  const timer = page.locator('.round-strip .timer');
  await expect(input).toBeEnabled();

  await page.clock.fastForward(10_000);
  serverSeconds = 10;
  await page.getByRole('button', { name: 'Pause' }).click();
  await expect(page.getByText('Paused — the music and the timer are stopped.')).toBeVisible();
  await expect(input).toBeDisabled();
  await expect(timer).toHaveText('20s left');
  await page.clock.fastForward(60_000);
  serverSeconds = 70;
  await expect(timer).toHaveText('20s left');

  await page.getByRole('button', { name: 'Resume' }).click();
  await expect(input).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Pause' })).toBeVisible();
  await page.clock.fastForward(5_000);
  serverSeconds = 75;
  // The page clock also runs in real time, so only check that the countdown moves again.
  await expect(timer).toHaveText(/^1?\ds left$/);
  await expect(timer).not.toHaveText('20s left');

  await input.fill('title');
  await input.press('Enter');
  await expect(page.getByRole('heading', { name: 'Secret title' })).toBeVisible();
  await page.getByRole('button', { name: 'Pause' }).click();
  await expect(page.getByText('Next track on hold')).toBeVisible();
  await page.clock.fastForward(20_000);
  serverSeconds = 95;
  await expect(page.getByText('Next track on hold')).toBeVisible();

  await page.getByRole('button', { name: 'Resume' }).click();
  await expect(page.getByText(/Next track in \d+ seconds/)).toBeVisible();
  expect(commands.filter((type) => type === 'PAUSE_ROUND')).toHaveLength(2);
  expect(commands.filter((type) => type === 'RESUME_ROUND')).toHaveLength(2);
});
