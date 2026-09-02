import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { ActiveSoloSession } from '../../../core/models/game-session.model';
import {
  CurrentSnapshotPayload,
  PrivateSessionEvent,
  PublicSessionEvent,
  SessionEventMetadata,
} from '../../../core/models/game-protocol.model';
import {
  AudioPlaybackService,
  AudioPlaybackStatus,
} from '../../../core/services/audio-playback.service';
import { GameSocketService, GameSocketStatus } from '../../../core/services/game-socket.service';
import { GameRoomComponent } from './game-room.component';

const SESSION_ID = '55555555-5555-4555-8555-555555555555';

const active: ActiveSoloSession = {
  sessionId: SESSION_ID,
  participantId: '66666666-6666-4666-8666-666666666666',
  state: 'CREATED',
  version: 0,
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
  maxRoundSeconds: 30,
};

const NOW = Date.parse('2026-09-02T12:00:00.000Z');

function meta(sessionVersion: number): SessionEventMetadata {
  return {
    protocolVersion: 1,
    eventId: '44444444-4444-4444-8444-444444444444',
    sessionId: SESSION_ID,
    sessionVersion,
    occurredAt: new Date(NOW).toISOString(),
  };
}

function roundStarted(roundIndex: number): PublicSessionEvent {
  return {
    metadata: meta(roundIndex + 1),
    type: 'ROUND_STARTED',
    payload: {
      roundIndex,
      totalRounds: 12,
      startsAt: new Date(NOW).toISOString(),
      deadline: new Date(NOW + 30_000).toISOString(),
      excerptStartSeconds: 15,
      playbackUrl: 'https://media.example/excerpt.mp3?sig=abc',
    },
  };
}

class FakeGameSocket {
  status$ = new BehaviorSubject<GameSocketStatus>('idle');
  publicEvents$ = new Subject<PublicSessionEvent>();
  privateEvents$ = new Subject<PrivateSessionEvent>();
  snapshots$ = new Subject<CurrentSnapshotPayload>();
  version = 0;
  connect = jasmine.createSpy('connect');
  disconnect = jasmine.createSpy('disconnect');
  startGame = jasmine.createSpy('startGame');
  submitAnswer = jasmine.createSpy('submitAnswer');
  skipRound = jasmine.createSpy('skipRound');
  advanceRound = jasmine.createSpy('advanceRound');
  requestSnapshot = jasmine.createSpy('requestSnapshot');
}

class FakeAudio {
  status$ = new BehaviorSubject<AudioPlaybackStatus>('idle');
  error$ = new BehaviorSubject<string | null>(null);
  preload = jasmine.createSpy('preload');
  scheduleRound = jasmine.createSpy('scheduleRound');
  resume = jasmine.createSpy('resume');
  stop = jasmine.createSpy('stop');
}

describe('GameRoomComponent', () => {
  let socket: FakeGameSocket;
  let audio: FakeAudio;

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(NOW));
    sessionStorage.clear();
    socket = new FakeGameSocket();
    audio = new FakeAudio();
  });

  afterEach(() => {
    jasmine.clock().uninstall();
    sessionStorage.clear();
  });

  async function setup(sessionId: string) {
    await TestBed.configureTestingModule({
      imports: [GameRoomComponent],
      providers: [
        provideRouter([]),
        { provide: GameSocketService, useValue: socket },
        { provide: AudioPlaybackService, useValue: audio },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ sessionId }) } },
        },
      ],
    }).compileComponents();
    return {
      navigate: spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true),
      create: (): ComponentFixture<GameRoomComponent> => {
        const fixture = TestBed.createComponent(GameRoomComponent);
        fixture.detectChanges();
        return fixture;
      },
    };
  }

  function text(fixture: ComponentFixture<GameRoomComponent>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('redirects to configuration when there is no stored session', async () => {
    const harness = await setup('unknown');
    harness.create();
    expect(harness.navigate).toHaveBeenCalledWith(['/lobby/create']);
    expect(socket.connect).not.toHaveBeenCalled();
  });

  it('connects and shows the setup card for a stored session', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(SESSION_ID);
    const fixture = harness.create();

    expect(socket.connect).toHaveBeenCalledWith(active);
    expect(text(fixture)).toContain('Setting up your game');
    expect(text(fixture)).toContain('Eighties essentials');
  });

  it('requests a snapshot once on first connect and starts a CREATED game', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(SESSION_ID);
    harness.create();

    socket.status$.next('connected');
    socket.status$.next('connected');
    expect(socket.requestSnapshot).toHaveBeenCalledTimes(1);

    socket.snapshots$.next(snapshot('CREATED'));
    expect(socket.startGame).toHaveBeenCalledTimes(1);
  });

  it('enters the round on ROUND_STARTED, schedules audio and counts down', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(SESSION_ID);
    const fixture = harness.create();

    socket.publicEvents$.next(roundStarted(0));
    fixture.detectChanges();

    expect(audio.scheduleRound).toHaveBeenCalledWith({
      url: 'https://media.example/excerpt.mp3?sig=abc',
      startsAt: new Date(NOW).toISOString(),
      deadline: new Date(NOW + 30_000).toISOString(),
      excerptStartSeconds: 15,
    });
    expect(text(fixture)).toContain('Track');
    expect(text(fixture)).toContain('of 12');
    expect(text(fixture)).toContain('30s left');

    jasmine.clock().mockDate(new Date(NOW + 5_000));
    jasmine.clock().tick(250);
    fixture.detectChanges();
    expect(text(fixture)).toContain('25s left');
  });

  it('submits an answer once and never twice for a rapid double submit', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(SESSION_ID);
    const fixture = harness.create();
    const component = fixture.componentInstance;
    socket.publicEvents$.next(roundStarted(0));

    component.answer = 'take on me';
    component.submit();
    expect(component.answer).toBe('');
    // Second click before the guard clears — the text is still on screen.
    component.answer = 'take on me';
    component.submit();

    expect(socket.submitAnswer).toHaveBeenCalledOnceWith('take on me');

    socket.privateEvents$.next({
      metadata: meta(2),
      recipientParticipantId: active.participantId,
      commandId: 'c1',
      type: 'ANSWER_REJECTED',
      payload: { reason: 'WRONG_ANSWER', retryAt: null },
    });
    component.answer = 'africa';
    component.submit();
    expect(socket.submitAnswer).toHaveBeenCalledWith('africa');
  });

  it('skips the round and disables commands while reconnecting', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(SESSION_ID);
    const fixture = harness.create();
    const component = fixture.componentInstance;
    socket.publicEvents$.next(roundStarted(0));

    component.skip();
    component.skip();
    expect(socket.skipRound).toHaveBeenCalledTimes(1);

    socket.status$.next('reconnecting');
    fixture.detectChanges();
    expect(text(fixture)).toContain('reconnecting');
    expect(component.canSubmit).toBeFalse();
    expect(component.canSkip).toBeFalse();
    const submit = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(submit.disabled).toBeTrue();
  });

  it('shows the revealed strip without exposing the title, then resumes on next round', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(SESSION_ID);
    const fixture = harness.create();
    socket.publicEvents$.next(roundStarted(0));

    socket.publicEvents$.next({
      metadata: meta(5),
      type: 'ROUND_REVEALED',
      payload: {
        roundIndex: 0,
        totalRounds: 12,
        outcome: 'TITLE_FOUND',
        title: 'Take On Me',
        artist: 'a-ha',
        composer: null,
        album: null,
        roundPoints: 120,
        totalScore: 120,
        transitionAt: new Date(NOW + 4_000).toISOString(),
      },
    });
    fixture.detectChanges();

    expect(audio.stop).toHaveBeenCalled();
    expect(text(fixture)).toContain('+120 points');
    expect(text(fixture)).not.toContain('Take On Me');
    expect(fixture.nativeElement.querySelector('#answer-input')).toBeNull();

    socket.publicEvents$.next(roundStarted(1));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#answer-input')).not.toBeNull();
  });

  it('renders the finished panel and replays', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(SESSION_ID);
    const fixture = harness.create();

    socket.publicEvents$.next({
      metadata: meta(40),
      type: 'GAME_FINISHED',
      payload: {
        totalScore: 940,
        totalRounds: 12,
        titlesFound: 8,
        skippedRounds: 2,
        timedOutRounds: 2,
      },
    });
    fixture.detectChanges();

    expect(text(fixture)).toContain('Game finished');
    expect(text(fixture)).toContain('940');
    (fixture.nativeElement.querySelector('.bt-btn-primary') as HTMLButtonElement).click();
    expect(harness.navigate).toHaveBeenCalledWith(['/lobby/create']);
  });

  it('recovers a PLAYING game from a snapshot', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(SESSION_ID);
    const fixture = harness.create();

    socket.snapshots$.next({
      gameStatus: 'PLAYING',
      currentRoundIndex: 3,
      totalRounds: 12,
      totalScore: 300,
      currentRound: {
        roundIndex: 3,
        status: 'PLAYING',
        startsAt: new Date(NOW - 6_000).toISOString(),
        deadline: new Date(NOW + 24_000).toISOString(),
        playbackUrl: 'https://media.example/r3.mp3',
        excerptStartSeconds: 10,
        outcome: null,
        title: null,
        artist: null,
        composer: null,
        album: null,
        advanceAt: null,
        roundPoints: 0,
      },
      discoveredAnswerTypes: [],
      expiresAt: active.expiresAt,
    });
    fixture.detectChanges();

    expect(socket.startGame).not.toHaveBeenCalled();
    expect(audio.scheduleRound).toHaveBeenCalled();
    expect(text(fixture)).toContain('Track');
    expect(text(fixture)).toContain('300');
  });

  it('enters the error phase on a non-recoverable game error', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(SESSION_ID);
    const fixture = harness.create();

    socket.privateEvents$.next({
      metadata: meta(9),
      recipientParticipantId: active.participantId,
      commandId: null,
      type: 'ERROR',
      payload: { code: 'SESSION_EXPIRED', message: 'This game has ended.', recoverable: false },
    });
    fixture.detectChanges();

    expect(text(fixture)).toContain('Something went wrong');
    expect(text(fixture)).toContain('This game has ended.');
  });

  it('tears down the socket and audio on destroy', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(SESSION_ID);
    harness.create().destroy();
    expect(socket.disconnect).toHaveBeenCalled();
    expect(audio.stop).toHaveBeenCalled();
  });
});

function snapshot(gameStatus: CurrentSnapshotPayload['gameStatus']): CurrentSnapshotPayload {
  return {
    gameStatus,
    currentRoundIndex: 0,
    totalRounds: 12,
    totalScore: 0,
    currentRound: null,
    discoveredAnswerTypes: [],
    expiresAt: active.expiresAt,
  };
}
