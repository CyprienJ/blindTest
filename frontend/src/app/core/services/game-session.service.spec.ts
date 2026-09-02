import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActiveSoloSession, SoloSessionResponse } from '../models/game-session.model';
import { GameSessionService } from './game-session.service';

const response: SoloSessionResponse = {
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
};

const active: ActiveSoloSession = {
  ...response,
  playlistName: 'Eighties essentials',
  trackCount: 12,
  maxRoundSeconds: 25,
};

describe('GameSessionService', () => {
  let service: GameSessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GameSessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('creates a solo session with the exact configuration payload', () => {
    service
      .createSolo({
        playlistId: 'playlist-1',
        trackCount: 12,
        maxRoundSeconds: 25,
        extensions: {},
      })
      .subscribe();

    const request = http.expectOne('/api/sessions');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      playlistId: 'playlist-1',
      trackCount: 12,
      maxRoundSeconds: 25,
      extensions: {},
    });
    request.flush(response);
  });

  it('round-trips the active session through sessionStorage for a fresh instance', () => {
    service.setActiveSession(active);
    expect(service.getActiveSession(active.sessionId)).toEqual(active);

    const reloaded = TestBed.runInInjectionContext(() => new GameSessionService());
    expect(reloaded.getActiveSession(active.sessionId)).toEqual(active);
  });

  it('returns null when the requested id does not match the stored session', () => {
    service.setActiveSession(active);
    expect(service.getActiveSession('another-id')).toBeNull();
  });

  it('clears the active session from memory and storage', () => {
    service.setActiveSession(active);
    service.clearActiveSession();
    expect(service.getActiveSession(active.sessionId)).toBeNull();
    expect(sessionStorage.getItem('active_solo_session')).toBeNull();
  });
});
