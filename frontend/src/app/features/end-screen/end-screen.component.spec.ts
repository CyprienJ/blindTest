import { HttpErrorResponse } from '@angular/common/http';
import { provideZoneChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { GameSummary } from '../../core/models/game-summary.model';
import { Lobby, RematchResponse } from '../../core/models/lobby.model';
import { GameSessionService } from '../../core/services/game-session.service';
import { GuestSessionService } from '../../core/services/guest-session.service';
import { LobbyService } from '../../core/services/lobby.service';
import { EndScreenComponent } from './end-screen.component';

const SESSION_ID = '55555555-5555-4555-8555-555555555555';

const summary: GameSummary = {
  sessionId: SESSION_ID,
  totalScore: 940,
  totalRounds: 2,
  titlesFound: 1,
  skippedRounds: 1,
  timedOutRounds: 0,
  startedAt: '2026-09-02T12:00:00.000Z',
  completedAt: '2026-09-02T12:02:30.000Z',
  rounds: [
    {
      roundIndex: 0,
      outcome: 'TITLE_FOUND',
      points: 120,
      title: 'Take On Me',
      artist: 'a-ha',
      composer: null,
      album: null,
      startedAt: '2026-09-02T12:00:00.000Z',
      revealedAt: '2026-09-02T12:00:04.500Z',
      completedAt: '2026-09-02T12:00:08.000Z',
      titleFound: true,
    },
    {
      roundIndex: 1,
      outcome: 'SKIPPED',
      points: 0,
      title: 'Africa',
      artist: 'Toto',
      composer: null,
      album: null,
      startedAt: '2026-09-02T12:00:30.000Z',
      revealedAt: '2026-09-02T12:00:33.000Z',
      completedAt: '2026-09-02T12:00:36.000Z',
      titleFound: false,
    },
  ],
  gameMode: 'SOLO',
  participantId: '66666666-6666-4666-8666-666666666666',
  standings: [],
};

class FakeGameSessionService {
  requests: string[] = [];
  private readonly subject = new Subject<GameSummary>();

  getSummary(sessionId: string) {
    this.requests.push(sessionId);
    return this.subject.asObservable();
  }

  emit(value: GameSummary): void {
    this.subject.next(value);
  }

  fail(error: HttpErrorResponse): void {
    this.subject.error(error);
  }
}

class FakeLobbyService {
  rematches: string[] = [];
  readonly result = new Subject<RematchResponse>();

  rematch(sessionId: string) {
    this.rematches.push(sessionId);
    return this.result.asObservable();
  }
}

const REOPENED_ID = '77777777-7777-4777-8777-777777777777';

const multiplayerSummary: GameSummary = {
  ...summary,
  gameMode: 'MULTIPLAYER',
  standings: [
    {
      participantId: summary.participantId,
      displayName: 'Friend',
      guest: true,
      rank: 1,
      finalScore: 940,
    },
  ],
} as GameSummary;

function reopenedLobby(): Lobby {
  return { sessionId: REOPENED_ID, code: 'ABC234', participantId: summary.participantId } as Lobby;
}

describe('EndScreenComponent', () => {
  let service: FakeGameSessionService;
  let lobbies: FakeLobbyService;

  beforeEach(() => {
    service = new FakeGameSessionService();
    lobbies = new FakeLobbyService();
  });

  afterEach(() => sessionStorage.clear());

  async function setup(sessionId: string | null) {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [EndScreenComponent],
      providers: [
        provideRouter([]),
        provideZoneChangeDetection(),
        { provide: GameSessionService, useValue: service },
        { provide: LobbyService, useValue: lobbies },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap(sessionId ? { sessionId } : {}) } },
        },
      ],
    }).compileComponents();
    return {
      navigate: spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true),
      create: async (): Promise<ComponentFixture<EndScreenComponent>> => {
        const fixture = TestBed.createComponent(EndScreenComponent);
        fixture.changeDetectorRef.detectChanges();
        fixture.autoDetectChanges();
        await fixture.whenStable();
        return fixture;
      },
    };
  }

  function text(fixture: ComponentFixture<EndScreenComponent>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('shows a loading state before the summary resolves', async () => {
    const harness = await setup(SESSION_ID);
    const fixture = await harness.create();

    expect(service.requests).toEqual([SESSION_ID]);
    expect(text(fixture)).toContain('Loading your summary');
  });

  it('renders the durable summary once it resolves', async () => {
    const harness = await setup(SESSION_ID);
    const fixture = await harness.create();

    service.emit(summary);
    fixture.changeDetectorRef.markForCheck();
    fixture.changeDetectorRef.detectChanges();

    const content = text(fixture);
    expect(content).toContain('940');
    expect(content).toContain('Take On Me');
    expect(content).toContain('a-ha');
    expect(content).toContain('Found');
    expect(content).toContain('4.5s');
    expect(content).toContain('+120');
    expect(content).toContain('Africa');
    expect(content).toContain('Skipped');
    expect(content).toContain('1 found · 1 skipped · 0 timed out');

    (fixture.nativeElement.querySelector('.bt-btn-primary') as HTMLButtonElement).click();
    expect(harness.navigate).toHaveBeenCalledWith(['/lobby/create']);
  });

  it('takes a multiplayer player back to the reopened lobby', async () => {
    const harness = await setup(SESSION_ID);
    const fixture = await harness.create();
    service.emit({ ...multiplayerSummary, standings: [] });
    fixture.changeDetectorRef.detectChanges();

    const back = fixture.nativeElement.querySelector('.bt-btn-primary') as HTMLButtonElement;
    expect(back.textContent).toContain('Back to lobby');
    expect(text(fixture)).toContain('New game');
    back.click();
    expect(lobbies.rematches).toEqual([SESSION_ID]);

    lobbies.result.next({ guestToken: null, lobby: reopenedLobby() });
    expect(harness.navigate).toHaveBeenCalledWith(['/lobby', 'ABC234']);
  });

  it('swaps a returning guest to the token for the reopened lobby', async () => {
    const harness = await setup(SESSION_ID);
    const guests = TestBed.inject(GuestSessionService);
    guests.set({
      token: 'old-token',
      code: 'ABC234',
      sessionId: SESSION_ID,
      participantId: summary.participantId,
      nickname: 'Friend',
    });
    const fixture = await harness.create();
    service.emit(multiplayerSummary);
    fixture.changeDetectorRef.detectChanges();

    fixture.componentInstance.backToLobby();
    lobbies.result.next({ guestToken: 'new-token', lobby: reopenedLobby() });

    expect(guests.get()).toEqual(
      jasmine.objectContaining({ token: 'new-token', sessionId: REOPENED_ID, nickname: 'Friend' }),
    );
    expect(guests.allows('/lobby/ABC234')).toBeTrue();
    expect(harness.navigate).toHaveBeenCalledWith(['/lobby', 'ABC234']);
  });

  it('explains when the lobby of the game has closed', async () => {
    const harness = await setup(SESSION_ID);
    const fixture = await harness.create();
    service.emit(multiplayerSummary);
    fixture.changeDetectorRef.detectChanges();

    fixture.componentInstance.backToLobby();
    lobbies.result.error(
      new HttpErrorResponse({ status: 404, error: { code: 'LOBBY_NOT_FOUND' } }),
    );
    fixture.changeDetectorRef.detectChanges();

    expect(text(fixture)).toContain("This game's lobby has closed.");
    expect(harness.navigate).not.toHaveBeenCalled();
  });

  it('shows a not-found message on a 404 and offers a way back', async () => {
    const harness = await setup(SESSION_ID);
    const fixture = await harness.create();

    service.fail(new HttpErrorResponse({ status: 404 }));
    fixture.changeDetectorRef.markForCheck();
    fixture.changeDetectorRef.detectChanges();

    expect(text(fixture)).toContain("couldn't find a summary");
    expect(fixture.nativeElement.querySelector('a[href="/lobby/create"]')).not.toBeNull();
  });

  it('shows a generic error message on an unexpected failure', async () => {
    const harness = await setup(SESSION_ID);
    const fixture = await harness.create();

    service.fail(new HttpErrorResponse({ status: 500 }));
    fixture.changeDetectorRef.detectChanges();

    expect(text(fixture)).toContain('Something went wrong');
  });

  it('shows an error state when the route has no session id', async () => {
    const harness = await setup(null);
    const fixture = await harness.create();

    expect(service.requests).toEqual([]);
    expect(text(fixture)).toContain('missing a game to summarize');
  });
});
