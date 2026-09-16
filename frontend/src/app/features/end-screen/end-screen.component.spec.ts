import { HttpErrorResponse } from '@angular/common/http';
import { provideZoneChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { GameSummary } from '../../core/models/game-summary.model';
import { GameSessionService } from '../../core/services/game-session.service';
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
    },
  ],
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

describe('EndScreenComponent', () => {
  let service: FakeGameSessionService;

  beforeEach(() => {
    service = new FakeGameSessionService();
  });

  async function setup(sessionId: string | null) {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [EndScreenComponent],
      providers: [
        provideRouter([]),
        provideZoneChangeDetection(),
        { provide: GameSessionService, useValue: service },
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
