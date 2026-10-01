import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { GameSessionService } from '../../../core/services/game-session.service';
import { CreateSessionComponent } from './create-session.component';

const playlist = {
  id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  name: 'Eighties essentials',
  description: 'Synth-pop classics',
  imageUrl: null,
  trackCount: 20,
};

function detail() {
  return {
    ...playlist,
    tracks: Array.from({ length: 20 }, (_, index) => ({
      id: `track-${index}`,
      title: `Track ${index}`,
      artist: 'Artist',
      album: null,
      composer: null,
      work: null,
      category: 'Pop',
      decade: 1980,
      durationSeconds: 200,
      imageUrl: null,
      // Three tracks per level from 1 to 5, then five unrated tracks.
      difficulty: index < 15 ? (index % 5) + 1 : null,
    })),
  };
}

const sessionResponse = {
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

describe('CreateSessionComponent', () => {
  let fixture: ComponentFixture<CreateSessionComponent>;
  let component: CreateSessionComponent;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    localStorage.setItem('access_token', 'access-token');
    localStorage.setItem(
      'current_user',
      JSON.stringify({
        id: '11111111-1111-4111-8111-111111111111',
        username: 'player',
        email: 'player@example.com',
        role: 'USER',
        avatarUrl: null,
      }),
    );
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  async function render(playlists: unknown[]): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [CreateSessionComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(CreateSessionComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture.detectChanges();

    http.expectOne('/api/playlists').flush(playlists);
    if (playlists.length > 0) {
      http.expectOne(`/api/playlists/${playlist.id}`).flush(detail());
    }
    fixture.detectChanges();
  }

  it('cannot submit without an eligible playlist', async () => {
    await render([]);

    component.createSession();

    http.expectNone('/api/sessions');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('creates the session and hands off to the game room', async () => {
    await render([playlist]);

    component.createSession();

    const request = http.expectOne('/api/sessions');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      playlistId: playlist.id,
      trackCount: 15,
      maxRoundSeconds: 30,
      extensions: {},
      difficultyLevels: [1, 2, 3, 4, 5],
      includeUnrated: true,
    });
    request.flush(sessionResponse);

    expect(router.navigate).toHaveBeenCalledWith(['/game', sessionResponse.sessionId]);
    const stored = TestBed.inject(GameSessionService).getActiveSession(sessionResponse.sessionId);
    expect(stored?.playlistName).toBe(playlist.name);
    expect(stored?.trackCount).toBe(15);
  });

  it('shows an actionable message and reloads playlists on an insufficient-tracks error', async () => {
    await render([playlist]);

    component.createSession();

    http
      .expectOne('/api/sessions')
      .flush(
        { code: 'GAME_PLAYLIST_INSUFFICIENT_TRACKS', message: 'not enough' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(component.submitError).toContain('15 rounds');
    expect(router.navigate).not.toHaveBeenCalled();
    // The catalogue is refreshed; the already-selected playlist detail is not re-fetched.
    http.expectOne('/api/playlists').flush([playlist]);
  });

  it('sends the selected difficulty levels and caps the track count to matching tracks', async () => {
    await render([playlist]);

    component.toggleDifficulty(1);
    component.toggleDifficulty(5);
    component.toggleDifficulty(null);

    expect(component.matchingTrackCount()).toBe(9);
    expect(component.trackCountMaximum()).toBe(9);
    expect(component.form.controls.trackCount.value).toBe(9);
    expect(component.difficultySummary()).toBe('2, 3, 4 ★');

    component.createSession();

    const request = http.expectOne('/api/sessions');
    expect(request.request.body).toEqual(
      jasmine.objectContaining({
        trackCount: 9,
        difficultyLevels: [2, 3, 4],
        includeUnrated: false,
      }),
    );
  });

  it('blocks creation when too few tracks match the selected difficulties', async () => {
    await render([playlist]);

    [1, 2, 3, 4].forEach((level) => component.toggleDifficulty(level));
    component.toggleDifficulty(null);
    fixture.detectChanges();

    expect(component.matchingTrackCount()).toBe(3);
    expect(component.difficultyError()).toContain('Only 3 tracks match');
    const createButton = fixture.nativeElement.querySelector('.create-button') as HTMLButtonElement;
    expect(createButton.disabled).toBeTrue();

    component.createSession();
    http.expectNone('/api/sessions');

    component.toggleDifficulty(5);
    expect(component.difficultyError()).toBe('Select at least one difficulty level.');
  });
});
