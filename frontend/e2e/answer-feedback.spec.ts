import { expect, test } from './fixtures/test';
import { installSession } from './support/auth';

const sessionId = '55555555-5555-4555-8555-555555555555';
const participantId = '66666666-6666-4666-8666-666666666666';
const publicDestination = `/topic/sessions/${sessionId}`;
const privateDestination = `/user/queue/sessions/${sessionId}`;
const now = new Date('2026-09-11T12:00:00Z');

test('shows private feedback and permits continued keyboard title guesses', async ({ page }) => {
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
          playlistName: 'Feedback test',
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

  const answers: string[] = [];
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
        totalRounds: 5,
        totalScore: 0,
        discoveredAnswerTypes: [],
        expiresAt: '2026-09-11T13:00:00Z',
        currentRound: {
          roundIndex: 0,
          status: 'PLAYING',
          startsAt: now.toISOString(),
          deadline: new Date(now.getTime() + 30000).toISOString(),
          playbackUrl: null,
          excerptStartSeconds: 0,
          outcome: null,
          title: null,
          artist: null,
          composer: null,
          album: null,
          streamingLinks: {},
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
          } else if (command.type === 'ADVANCE_ROUND') {
            advanceCount++;
            emit(publicDestination, 'ROUND_STARTED', {
              roundIndex: 1,
              totalRounds: 5,
              startsAt: new Date(now.getTime() + 15000).toISOString(),
              deadline: new Date(now.getTime() + 45000).toISOString(),
              playbackUrl: null,
              excerptStartSeconds: 0,
            });
          } else if (command.type === 'SUBMIT_ANSWER') {
            answers.push(command.answer);
            const secondary = { artist: 'ARTIST', composer: 'COMPOSER', album: 'ALBUM' }[
              command.answer as string
            ];
            if (secondary) {
              emit(privateDestination, 'SECONDARY_MATCH', {
                answerType: secondary,
                matchedValue: `Known ${command.answer}`,
                alreadyFound: answers.filter((answer) => answer === command.answer).length > 1,
              });
            } else if (command.answer === 'fast') {
              emit(privateDestination, 'ANSWER_REJECTED', {
                reason: 'COOLDOWN',
                retryAt: new Date(now.getTime() + 10000).toISOString(),
              });
            } else if (command.answer === 'title') {
              emit(publicDestination, 'ROUND_REVEALED', {
                roundIndex: 0,
                totalRounds: 5,
                outcome: 'TITLE_FOUND',
                title: 'Secret title',
                artist: 'Known artist',
                composer: null,
                album: null,
                streamingLinks: {},
                roundPoints: 100,
                totalScore: 100,
                transitionAt: new Date(now.getTime() + 14000).toISOString(),
              });
            } else {
              emit(privateDestination, 'ANSWER_REJECTED', {
                reason: 'WRONG_ANSWER',
                retryAt: null,
              });
            }
          }
        }
      }
    });
  });

  await page.goto(`/game/${sessionId}`);
  const input = page.getByRole('textbox', { name: 'Your answer' });
  const feedback = page.locator('#answer-feedback');
  await expect(input).toBeEnabled();
  async function guess(answer: string) {
    await input.fill(answer);
    await input.press('Enter');
    await expect(input).toHaveValue('');
    await expect(input).toBeFocused();
  }
  await guess('wrong');
  await expect(feedback).toContainText('Not a match');
  await expect(page.getByText('Secret title')).toHaveCount(0);
  for (const answer of ['artist', 'artist', 'composer', 'album']) {
    await guess(answer);
    await expect(feedback).toContainText(`Known ${answer}. Keep guessing the title.`);
    if (answers.filter((value) => value === answer).length > 1) {
      await expect(feedback).toContainText('already found');
    }
    await expect(page.locator('.score-card .score')).toHaveText('0');
    await expect(page.locator('.reveal-strip')).toHaveCount(0);
  }
  await expect(
    page.getByRole('list', { name: 'Your discoveries' }).getByRole('listitem'),
  ).toHaveCount(3);
  await guess('fast');
  await expect(feedback).toContainText('answering too quickly');
  await input.fill('title');
  await expect(page.getByRole('button', { name: 'Submit', exact: true })).toBeDisabled();
  await input.press('Enter');
  expect(answers).not.toContain('title');
  await page.clock.fastForward(10000);
  await expect(page.getByRole('button', { name: 'Submit', exact: true })).toBeEnabled();
  await input.press('Enter');
  await expect(page.getByText('You found the title', { exact: true })).toBeVisible();
  await expect(page.locator('.score-card .score')).toHaveText('100');
  await expect(feedback).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Secret title' })).toBeVisible();
  await page.clock.fastForward(5000);
  await expect(page.getByText('Waiting for the next track…')).toBeVisible();
  await expect(input).toHaveCount(0);
  expect(advanceCount).toBe(0);
  await page.getByRole('button', { name: 'Next now', exact: true }).click();
  await expect(input).toBeEnabled();
  await expect(page.getByRole('heading', { name: 'Secret title' })).toHaveCount(0);
  await expect(page.locator('.track-position')).toContainText('2');
  expect(advanceCount).toBe(1);
});
