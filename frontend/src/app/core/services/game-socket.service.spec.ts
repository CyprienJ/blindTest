import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { RxStompState } from '@stomp/rx-stomp';
import { BehaviorSubject, Subject } from 'rxjs';
import { ActiveSoloSession } from '../models/game-session.model';
import { PublicSessionEvent, SessionEventMetadata } from '../models/game-protocol.model';
import { AuthService } from './auth.service';
import { GAME_SOCKET_CLIENT, GameSocketService } from './game-socket.service';
import { ServerClockService } from './server-clock.service';

const SESSION_ID = '55555555-5555-4555-8555-555555555555';

const session: ActiveSoloSession = {
  sessionId: SESSION_ID,
  participantId: '66666666-6666-4666-8666-666666666666',
  state: 'CREATED',
  version: 3,
  expiresAt: '2026-09-02T12:30:00Z',
  connection: {
    protocolVersion: 1,
    webSocketEndpoint: '/ws',
    sockJsEndpoint: '/ws-sockjs',
    commandDestination: `/app/sessions/${SESSION_ID}`,
    eventDestination: `/topic/sessions/${SESSION_ID}`,
    privateDestination: `/user/queue/sessions/${SESSION_ID}`,
  },
  playlistName: 'Eighties essentials',
  trackCount: 12,
  maxRoundSeconds: 25,
};

function meta(sessionVersion: number): SessionEventMetadata {
  return {
    protocolVersion: 1,
    eventId: '44444444-4444-4444-8444-444444444444',
    sessionId: SESSION_ID,
    sessionVersion,
    occurredAt: '2026-09-02T12:00:00Z',
  };
}

class FakeRxStomp {
  config: Record<string, unknown> = {};
  readonly connectionState$ = new BehaviorSubject<RxStompState>(RxStompState.CLOSED);
  readonly stompErrors$ = new Subject<{ headers: Record<string, string> }>();
  readonly watched = new Map<string, Subject<{ body: string }>>();
  readonly published: { destination?: string; body?: string; headers?: Record<string, string> }[] =
    [];
  activateCalls = 0;
  deactivateCalls = 0;
  readonly stompClient = { connectHeaders: {} as Record<string, string> };

  configure(config: Record<string, unknown>): void {
    this.config = { ...this.config, ...config };
  }
  activate(): void {
    this.activateCalls += 1;
  }
  deactivate(): Promise<void> {
    this.deactivateCalls += 1;
    return Promise.resolve();
  }
  watch(destination: string): Subject<{ body: string }> {
    const subject = new Subject<{ body: string }>();
    this.watched.set(destination, subject);
    return subject;
  }
  publish(params: { destination?: string; body?: string; headers?: Record<string, string> }): void {
    this.published.push(params);
  }
  frame(destination: string, payload: unknown): void {
    this.watched.get(destination)?.next({ body: JSON.stringify(payload) });
  }
}

describe('GameSocketService', () => {
  let clients: FakeRxStomp[];
  let service: GameSocketService;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('access_token', 'access-token');
    localStorage.setItem(
      'current_user',
      JSON.stringify({ id: 'u1', username: 'p', email: 'p@e.com', role: 'USER', avatarUrl: null }),
    );
    clients = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        {
          provide: GAME_SOCKET_CLIENT,
          useValue: () => {
            const client = new FakeRxStomp();
            clients.push(client);
            return client;
          },
        },
      ],
    });
    service = TestBed.inject(GameSocketService);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => localStorage.clear());

  function current(): FakeRxStomp {
    return clients[clients.length - 1];
  }

  it('connects with the access token and watches both destinations once', () => {
    service.connect(session);
    const client = current();

    expect(client.activateCalls).toBe(1);
    expect(client.config['brokerURL']).toContain('/ws');
    expect(client.config['connectHeaders']).toEqual({ Authorization: 'Bearer access-token' });
    expect([...client.watched.keys()]).toEqual([
      session.connection.eventDestination,
      session.connection.privateDestination,
    ]);
  });

  it('is idempotent for the same session and re-connects for a different one', () => {
    service.connect(session);
    service.connect(session);
    expect(clients.length).toBe(1);

    service.connect({
      ...session,
      sessionId: 'other',
      connection: { ...session.connection, commandDestination: '/app/sessions/other' },
    });
    expect(clients.length).toBe(2);
    expect(clients[0].deactivateCalls).toBe(1);
  });

  it('re-emits shared and private events and tracks the session version', () => {
    const publicEvents: PublicSessionEvent[] = [];
    const snapshots: unknown[] = [];
    service.publicEvents$.subscribe((event) => publicEvents.push(event));
    service.snapshots$.subscribe((payload) => snapshots.push(payload));

    service.connect(session);
    current().frame(session.connection.eventDestination, {
      metadata: meta(7),
      type: 'ROUND_PREPARING',
      payload: { roundIndex: 1, totalRounds: 12 },
    });
    current().frame(session.connection.privateDestination, {
      metadata: meta(8),
      recipientParticipantId: session.participantId,
      commandId: null,
      type: 'CURRENT_SNAPSHOT',
      payload: {
        gameStatus: 'PLAYING',
        currentRoundIndex: 1,
        totalRounds: 12,
        totalScore: 40,
        currentRound: null,
        discoveredAnswerTypes: [],
        expiresAt: session.expiresAt,
      },
    });

    expect(publicEvents.map((event) => event.type)).toEqual(['ROUND_PREPARING']);
    expect(snapshots.length).toBe(1);
    expect(service.version).toBe(8);
  });

  it('feeds every event timestamp to the server clock', () => {
    const observe = spyOn(TestBed.inject(ServerClockService), 'observe');

    service.connect(session);
    current().frame(session.connection.eventDestination, {
      metadata: meta(7),
      type: 'ROUND_PREPARING',
      payload: { roundIndex: 1, totalRounds: 12 },
    });
    current().frame(session.connection.privateDestination, {
      metadata: { ...meta(8), occurredAt: '2026-09-02T12:00:01Z' },
      recipientParticipantId: session.participantId,
      commandId: null,
      type: 'ERROR',
      payload: { code: 'SESSION_RATE_LIMITED', recoverable: true },
    });

    expect(observe.calls.allArgs()).toEqual([['2026-09-02T12:00:00Z'], ['2026-09-02T12:00:01Z']]);
  });

  it('builds typed commands with the current version and JSON content type', () => {
    service.connect(session);
    current().frame(session.connection.eventDestination, {
      metadata: meta(9),
      type: 'ROUND_PREPARING',
      payload: { roundIndex: 0, totalRounds: 12 },
    });

    service.submitAnswer('take on me');
    service.startGame();

    const answer = JSON.parse(current().published[0].body ?? '{}');
    expect(current().published[0].destination).toBe(session.connection.commandDestination);
    expect(current().published[0].headers).toEqual({ 'content-type': 'application/json' });
    expect(answer).toEqual(
      jasmine.objectContaining({
        protocolVersion: 1,
        expectedSessionVersion: 9,
        type: 'SUBMIT_ANSWER',
        answer: 'take on me',
      }),
    );
    expect(answer.commandId).toMatch(/^[0-9a-f-]{36}$/);

    const start = JSON.parse(current().published[1].body ?? '{}');
    expect(start.type).toBe('START_GAME');
    expect('answer' in start).toBeFalse();
  });

  it('requests a snapshot only after a reconnection and never re-watches', () => {
    service.connect(session);
    const client = current();

    client.connectionState$.next(RxStompState.OPEN);
    expect(client.published.length).toBe(0);

    client.connectionState$.next(RxStompState.CLOSED);
    client.connectionState$.next(RxStompState.CONNECTING);
    client.connectionState$.next(RxStompState.OPEN);

    expect(client.published.length).toBe(1);
    expect(JSON.parse(client.published[0].body ?? '{}').type).toBe('REQUEST_SNAPSHOT');
    expect([...client.watched.keys()].length).toBe(2);
  });

  it('reports connecting / connected / reconnecting status', () => {
    const seen: string[] = [];
    service.status$.subscribe((status) => seen.push(status));

    service.connect(session);
    const client = current();
    client.connectionState$.next(RxStompState.CONNECTING);
    client.connectionState$.next(RxStompState.OPEN);
    client.connectionState$.next(RxStompState.CLOSED);

    expect(seen).toEqual(['idle', 'connecting', 'connected', 'reconnecting']);
  });

  it('surfaces a stomp error frame as a fatal, terminal error and stops reconnecting', () => {
    const fatalMessages: string[] = [];
    const statuses: string[] = [];
    service.fatalError$.subscribe((message) => fatalMessages.push(message));
    service.status$.subscribe((status) => statuses.push(status));

    service.connect(session);
    const client = current();
    client.connectionState$.next(RxStompState.OPEN);
    client.stompErrors$.next({
      headers: { message: 'Session is unavailable or access is denied' },
    });

    expect(fatalMessages).toEqual(['This game session is no longer available.']);
    expect(client.deactivateCalls).toBe(1);
    expect(statuses[statuses.length - 1]).toBe('idle');
  });

  it('reports the same message when the error frame has none', () => {
    const fatalMessages: string[] = [];
    service.fatalError$.subscribe((message) => fatalMessages.push(message));

    service.connect(session);
    current().stompErrors$.next({ headers: {} });

    expect(fatalMessages).toEqual(['This game session is no longer available.']);
  });

  it('tears down on disconnect and on logout', () => {
    service.connect(session);
    const first = current();
    service.disconnect();
    expect(first.deactivateCalls).toBe(1);
    service.startGame();
    expect(first.published.length).toBe(0);

    service.connect(session);
    const second = current();
    auth.clearSession();
    expect(second.deactivateCalls).toBe(1);
  });
});
