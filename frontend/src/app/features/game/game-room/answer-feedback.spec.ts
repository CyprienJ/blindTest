import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { GameRoomComponent } from './game-room.component';
import { GameSessionService } from '../../../core/services/game-session.service';
import { GameSocketService } from '../../../core/services/game-socket.service';
import { AudioPlaybackService } from '../../../core/services/audio-playback.service';
import {
  CurrentSnapshotPayload,
  PrivateSessionEvent,
  PublicSessionEvent,
  SecondaryAnswerType,
} from '../../../core/models/game-protocol.model';

describe('Game room answer feedback', () => {
  let fixture: ComponentFixture<GameRoomComponent>;
  let privateEvents: Subject<PrivateSessionEvent>;
  let publicEvents: Subject<PublicSessionEvent>;
  let snapshots: Subject<CurrentSnapshotPayload>;
  let socket: { submitAnswer: jasmine.Spy; requestSnapshot: jasmine.Spy };
  let audio: { stop: jasmine.Spy };

  beforeEach(async () => {
    privateEvents = new Subject();
    publicEvents = new Subject();
    snapshots = new Subject();
    socket = { submitAnswer: jasmine.createSpy(), requestSnapshot: jasmine.createSpy() };
    audio = { stop: jasmine.createSpy() };
    await TestBed.configureTestingModule({
      imports: [GameRoomComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ sessionId: 'session' }) } },
        },
        { provide: GameSessionService, useValue: { getActiveSession: () => ({ trackCount: 5 }) } },
        {
          provide: GameSocketService,
          useValue: {
            ...socket,
            connect: () => undefined,
            disconnect: () => undefined,
            status$: new BehaviorSubject('connected'),
            snapshots$: snapshots,
            privateEvents$: privateEvents,
            publicEvents$: publicEvents,
          },
        },
        {
          provide: AudioPlaybackService,
          useValue: {
            ...audio,
            scheduleRound: () => undefined,
            status$: new BehaviorSubject('playing'),
            error$: new BehaviorSubject(null),
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(GameRoomComponent);
    fixture.detectChanges();
  });

  function startRound(): void {
    publicEvents.next({
      type: 'ROUND_STARTED',
      payload: {
        roundIndex: 0,
        totalRounds: 5,
        startsAt: new Date().toISOString(),
        deadline: new Date(Date.now() + 30000).toISOString(),
        playbackUrl: null,
        excerptStartSeconds: 0,
      },
    } as PublicSessionEvent);
    fixture.detectChanges();
    tick();
  }

  function send(event: Pick<PrivateSessionEvent, 'type' | 'payload'>): void {
    privateEvents.next(event as PrivateSessionEvent);
    fixture.detectChanges();
    tick();
  }

  function feedback(): string {
    return fixture.nativeElement.querySelector('#answer-feedback').textContent;
  }

  it('clears submitted text, restores focus, and shows neutral wrong-answer feedback', fakeAsync(() => {
    startRound();
    fixture.componentInstance.answer = 'wrong guess';
    fixture.componentInstance.submit();
    fixture.detectChanges();
    send({ type: 'ANSWER_REJECTED', payload: { reason: 'WRONG_ANSWER', retryAt: null } });
    expect(socket.submitAnswer).toHaveBeenCalledWith('wrong guess');
    expect(fixture.componentInstance.answer).toBe('');
    expect(feedback()).toContain('Not a match. Keep trying!');
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('input'));
    expect(fixture.componentInstance.phase).toBe('playing');
    tick(400);
    fixture.destroy();
  }));

  for (const type of ['ARTIST', 'COMPOSER', 'ALBUM'] as SecondaryAnswerType[]) {
    it(`keeps a ${type} discovery private to the answer area without ending or scoring the round`, fakeAsync(() => {
      startRound();
      send({
        type: 'SECONDARY_MATCH',
        payload: { answerType: type, matchedValue: 'Known value', alreadyFound: false },
      });
      expect(feedback()).toContain('found: Known value. Keep guessing the title.');
      expect(fixture.componentInstance.phase).toBe('playing');
      expect(fixture.componentInstance.totalScore).toBe(0);
      expect(fixture.componentInstance.lastRoundPoints).toBeNull();
      expect(audio.stop).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('.reveal-strip')).toBeNull();
      send({
        type: 'SECONDARY_MATCH',
        payload: { answerType: type, matchedValue: 'Known value', alreadyFound: true },
      });
      expect(feedback()).toContain('already found');
      expect(fixture.nativeElement.querySelectorAll('.discoveries li').length).toBe(1);
      fixture.componentInstance.answer = 'title attempt';
      fixture.componentInstance.submit();
      expect(socket.submitAnswer).toHaveBeenCalledWith('title attempt');
      tick(400);
      fixture.destroy();
    }));
  }

  it('handles already-found rejection and preserves a newly typed guess on delayed feedback', fakeAsync(() => {
    startRound();
    fixture.componentInstance.answer = 'next guess';
    send({ type: 'ANSWER_REJECTED', payload: { reason: 'ALREADY_FOUND', retryAt: null } });
    expect(feedback()).toContain('Already found');
    expect(fixture.componentInstance.answer).toBe('next guess');
    fixture.destroy();
  }));

  it('blocks submission until retryAt while allowing typing, then permits another guess', fakeAsync(() => {
    startRound();
    send({
      type: 'ANSWER_REJECTED',
      payload: { reason: 'COOLDOWN', retryAt: new Date(Date.now() + 2000).toISOString() },
    });
    fixture.componentInstance.answer = 'next guess';
    fixture.componentInstance.submit();
    expect(socket.submitAnswer).not.toHaveBeenCalled();
    expect(feedback()).toContain('Wait 2s');
    expect(fixture.nativeElement.querySelector('input').disabled).toBeFalse();
    tick(2000);
    fixture.componentInstance.submit();
    expect(socket.submitAnswer).toHaveBeenCalledWith('next guess');
    tick(400);
    fixture.destroy();
  }));

  it('restores discovery categories from a snapshot without inventing their values', fakeAsync(() => {
    startRound();
    snapshots.next({
      gameStatus: 'PLAYING',
      currentRoundIndex: 0,
      totalRounds: 5,
      totalScore: 0,
      discoveredAnswerTypes: ['ARTIST'],
      currentRound: {
        status: 'PLAYING',
        startsAt: new Date().toISOString(),
        deadline: new Date(Date.now() + 30000).toISOString(),
        playbackUrl: null,
        excerptStartSeconds: 0,
      },
    } as CurrentSnapshotPayload);
    fixture.detectChanges();
    tick();
    expect(fixture.nativeElement.querySelector('.discoveries').textContent.trim()).toBe(
      'Artist found',
    );
    startRound();
    expect(fixture.nativeElement.querySelector('.discoveries')).toBeNull();
    fixture.destroy();
  }));

  it('clears feedback on reveal and ignores late private feedback outside play', fakeAsync(() => {
    startRound();
    send({
      type: 'SECONDARY_MATCH',
      payload: { answerType: 'ARTIST', matchedValue: 'Artist', alreadyFound: false },
    });
    publicEvents.next({
      type: 'ROUND_REVEALED',
      payload: { outcome: 'TITLE_FOUND', roundPoints: 100, totalScore: 100 },
    } as PublicSessionEvent);
    send({ type: 'ANSWER_REJECTED', payload: { reason: 'WRONG_ANSWER', retryAt: null } });
    expect(fixture.componentInstance.feedback).toBe('');
    expect(fixture.componentInstance.discoveries).toEqual({});
    expect(fixture.componentInstance.phase).toBe('revealed');
    expect(fixture.componentInstance.totalScore).toBe(100);
    fixture.destroy();
  }));
});
