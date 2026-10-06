import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZoneChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { PublicSessionEvent } from '../../../core/models/game-protocol.model';
import { Lobby } from '../../../core/models/lobby.model';
import { GameSessionService } from '../../../core/services/game-session.service';
import { GameSocketService, GameSocketStatus } from '../../../core/services/game-socket.service';
import { GuestSessionService } from '../../../core/services/guest-session.service';
import { LobbyRoomComponent } from './lobby-room.component';

const HOST = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const GUEST = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

function lobby(participantId: string, overrides: Partial<Lobby> = {}): Lobby {
  return {
    sessionId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    code: 'ABC234',
    participantId,
    hostParticipantId: HOST,
    participants: [
      { participantId: HOST, displayName: 'Host', ready: false, host: true, guest: false },
      { participantId: GUEST, displayName: 'Friend', ready: false, host: false, guest: false },
    ],
    canStart: false,
    maxParticipants: 10,
    version: 1,
    expiresAt: '2026-10-02T13:00:00Z',
    connection: {
      protocolVersion: 1,
      webSocketEndpoint: '/ws',
      sockJsEndpoint: '/ws-sockjs',
      commandDestination: '/app/sessions/x',
      eventDestination: '/topic/sessions/x',
      privateDestination: '/user/queue/sessions/x',
    },
    playlistName: 'Party',
    trackCount: 5,
    maxRoundSeconds: 30,
    audioMode: 'EVERYONE',
    ...overrides,
  };
}

function event(type: string, payload: unknown): PublicSessionEvent {
  return {
    metadata: {
      protocolVersion: 1,
      eventId: 'e',
      sessionId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
      sessionVersion: 2,
      occurredAt: '2026-10-02T12:00:00Z',
    },
    type,
    payload,
  } as PublicSessionEvent;
}

class FakeGameSocket {
  status$ = new BehaviorSubject<GameSocketStatus>('idle');
  publicEvents$ = new Subject<PublicSessionEvent>();
  connect = jasmine.createSpy('connect');
  disconnect = jasmine.createSpy('disconnect');
}

describe('LobbyRoomComponent', () => {
  let socket: FakeGameSocket;
  let http: HttpTestingController;
  let navigate: jasmine.Spy;

  async function create(participantId: string) {
    socket = new FakeGameSocket();
    await TestBed.configureTestingModule({
      imports: [LobbyRoomComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideZoneChangeDetection({ eventCoalescing: true }),
        { provide: GameSocketService, useValue: socket },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ code: 'abc234' }) } },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(LobbyRoomComponent);
    fixture.autoDetectChanges();
    http.expectOne('/api/lobbies/ABC234').flush(lobby(participantId));
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('shows the code and players and listens on the session topic', async () => {
    const fixture = await create(GUEST);
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('ABC234');
    expect(text).toContain('Host');
    expect(text).toContain('Friend');
    expect(text).toContain("I'm ready");
    expect(socket.connect).toHaveBeenCalledWith(
      jasmine.objectContaining({ sessionId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc' }),
    );

    // The first connection re-reads the lobby to catch updates sent before subscribing.
    socket.status$.next('connected');
    http.expectOne('/api/lobbies/ABC234').flush(lobby(GUEST));
  });

  it('applies live updates and notices when this player is removed', async () => {
    const fixture = await create(GUEST);
    socket.publicEvents$.next(
      event('LOBBY_UPDATED', {
        code: 'ABC234',
        hostParticipantId: HOST,
        participants: [
          { participantId: HOST, displayName: 'Host', ready: false, host: true, guest: false },
          { participantId: GUEST, displayName: 'Friend', ready: true, host: false, guest: false },
        ],
        canStart: true,
        audioMode: 'EVERYONE',
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('2/2 ready');

    socket.publicEvents$.next(
      event('LOBBY_UPDATED', {
        code: 'ABC234',
        hostParticipantId: HOST,
        participants: [
          { participantId: HOST, displayName: 'Host', ready: false, host: true, guest: false },
        ],
        canStart: false,
        audioMode: 'EVERYONE',
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('The host removed you from the lobby.');
    expect(socket.disconnect).toHaveBeenCalled();
  });

  it('enters the game as a non-host when the lobby starts', async () => {
    await create(GUEST);
    socket.publicEvents$.next(event('LOBBY_STARTED', { totalRounds: 5 }));

    const session = TestBed.inject(GameSessionService).getActiveSession(
      'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    );
    expect(session?.host).toBeFalse();
    expect(session?.version).toBe(0);
    expect(navigate).toHaveBeenCalledWith(['/game', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc']);
  });

  it('lets the host start once everyone is ready', async () => {
    const fixture = await create(HOST);
    const start = () =>
      (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.start')!;
    expect(start().disabled).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Waiting for every player to be ready.');

    fixture.componentInstance.lobby = { ...fixture.componentInstance.lobby!, canStart: true };
    fixture.detectChanges();
    await fixture.whenStable();
    start().click();
    http.expectOne('/api/lobbies/ABC234/start').flush({ sessionId: 'x' });

    const session = TestBed.inject(GameSessionService).getActiveSession(
      'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    );
    expect(session?.host).toBeTrue();
    expect(navigate).toHaveBeenCalledWith(['/game', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc']);
  });

  it('lets the host keep the music on its own device and carries it into the game', async () => {
    const fixture = await create(HOST);
    const buttons = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>(
        '.audio-switch button',
      ),
    );
    buttons.find((button) => button.textContent?.includes('Only my device'))!.click();
    const request = http.expectOne('/api/lobbies/ABC234/audio-mode');
    expect(request.request.body).toEqual({ audioMode: 'HOST_ONLY' });
    request.flush(lobby(HOST, { audioMode: 'HOST_ONLY', canStart: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain("Host's device only");

    socket.publicEvents$.next(event('LOBBY_STARTED', { totalRounds: 5 }));
    const session = TestBed.inject(GameSessionService).getActiveSession(
      'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    );
    expect(session?.audioMode).toBe('HOST_ONLY');
  });

  it('tells other players when only the host plays the music', async () => {
    const fixture = await create(GUEST);
    fixture.componentInstance.lobby = {
      ...fixture.componentInstance.lobby!,
      audioMode: 'HOST_ONLY',
    };
    fixture.detectChanges();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.audio-switch')).toBeNull();
    expect(element.textContent).toContain("The music plays on the host's device only");
  });

  it('sends a leaving guest back to the public page and forgets its token', async () => {
    const fixture = await create(GUEST);
    const guests = TestBed.inject(GuestSessionService);
    guests.set({
      token: 'guest-token',
      code: 'ABC234',
      sessionId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
      participantId: GUEST,
      nickname: 'Friend',
    });
    fixture.componentInstance.leave();
    http.expectOne('/api/lobbies/ABC234/participants/me').flush(null);

    expect(navigate).toHaveBeenCalledWith(['/']);
    expect(guests.get()).toBeNull();
  });

  it('explains an unavailable lobby', async () => {
    socket = new FakeGameSocket();
    await TestBed.configureTestingModule({
      imports: [LobbyRoomComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: GameSocketService, useValue: socket },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ code: 'ZZZZZZ' }) } },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(LobbyRoomComponent);
    fixture.autoDetectChanges();
    http
      .expectOne('/api/lobbies/ZZZZZZ')
      .flush({ code: 'LOBBY_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('This lobby is no longer open.');
    expect(socket.connect).not.toHaveBeenCalled();
  });
});
