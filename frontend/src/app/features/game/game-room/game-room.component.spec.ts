import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { ActiveSoloSession } from '../../../core/models/game-session.model';
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

async function render(sessionId: string): Promise<{
  fixture: ComponentFixture<GameRoomComponent>;
  navigate: jasmine.Spy;
}> {
  await TestBed.configureTestingModule({
    imports: [GameRoomComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ sessionId }) } },
      },
    ],
  }).compileComponents();
  const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  const fixture = TestBed.createComponent(GameRoomComponent);
  fixture.detectChanges();
  return { fixture, navigate };
}

describe('GameRoomComponent', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => sessionStorage.clear());

  it('shows the setup placeholder for a known active session', async () => {
    sessionStorage.setItem('active_solo_session', JSON.stringify(active));

    const { fixture, navigate } = await render(active.sessionId);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Setting up your game');
    expect(text).toContain('Eighties essentials');
    expect(text).toContain('12 tracks');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('redirects to configuration when there is no matching session', async () => {
    const { navigate } = await render('unknown-session');
    expect(navigate).toHaveBeenCalledWith(['/lobby/create']);
  });
});
