import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { ActiveSoloSession } from '../../../core/models/game-session.model';
import { GAME_SOCKET_CLIENT, GameSocketService } from '../../../core/services/game-socket.service';
import { GameRoomComponent } from './game-room.component';

const active: ActiveSoloSession = {
  sessionId: '55555555-5555-4555-8555-555555555555',
  participantId: '66666666-6666-4666-8666-666666666666',
  state: 'CREATED',
  version: 0,
  expiresAt: '2026-09-02T12:30:00Z',
  connection: {
    protocolVersion: 1,
    webSocketEndpoint: '/ws',
    sockJsEndpoint: '/ws-sockjs',
    commandDestination: '/app/sessions/55555555-5555-4555-8555-555555555555',
    eventDestination: '/topic/sessions/55555555-5555-4555-8555-555555555555',
    privateDestination: '/user/queue/sessions/55555555-5555-4555-8555-555555555555',
  },
  playlistName: 'Eighties essentials',
  trackCount: 12,
  maxRoundSeconds: 25,
};

function fakeClient() {
  return {
    connectionState$: new BehaviorSubject(3),
    configure: () => undefined,
    activate: () => undefined,
    deactivate: () => Promise.resolve(),
    watch: () => new Subject(),
    publish: () => undefined,
    stompClient: { connectHeaders: {} },
  };
}

async function setup(sessionId: string) {
  await TestBed.configureTestingModule({
    imports: [GameRoomComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: GAME_SOCKET_CLIENT, useValue: fakeClient },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ sessionId }) } },
      },
    ],
  }).compileComponents();

  const socket = TestBed.inject(GameSocketService);
  return {
    socket,
    navigate: spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true),
    connect: spyOn(socket, 'connect').and.callThrough(),
    disconnect: spyOn(socket, 'disconnect').and.callThrough(),
    create: () => {
      const fixture = TestBed.createComponent(GameRoomComponent);
      fixture.detectChanges();
      return fixture;
    },
  };
}

describe('GameRoomComponent', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => sessionStorage.clear());

  it('shows the setup placeholder and connects for a known active session', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(active.sessionId);

    const fixture = harness.create();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Setting up your game');
    expect(text).toContain('Eighties essentials');
    expect(text).toContain('12 tracks');
    expect(harness.connect).toHaveBeenCalledWith(active);
    expect(harness.navigate).not.toHaveBeenCalled();
  });

  it('redirects to configuration when there is no matching session', async () => {
    const harness = await setup('unknown-session');
    harness.create();
    expect(harness.navigate).toHaveBeenCalledWith(['/lobby/create']);
    expect(harness.connect).not.toHaveBeenCalled();
  });

  it('disconnects the socket on destroy', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));
    const harness = await setup(active.sessionId);
    harness.create().destroy();
    expect(harness.disconnect).toHaveBeenCalled();
  });
});
