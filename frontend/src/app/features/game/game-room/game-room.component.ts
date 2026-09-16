import {
  Component,
  DestroyRef,
  ElementRef,
  inject,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { timer } from 'rxjs';
import { ActiveSoloSession } from '../../../core/models/game-session.model';
import {
  CurrentSnapshotPayload,
  CurrentRoundProjection,
  GameFinishedPayload,
  PrivateSessionEvent,
  PublicSessionEvent,
  RoundOutcome,
  RoundStartedPayload,
  SecondaryAnswerType,
} from '../../../core/models/game-protocol.model';
import {
  AudioPlaybackService,
  AudioPlaybackStatus,
} from '../../../core/services/audio-playback.service';
import { GameSessionService } from '../../../core/services/game-session.service';
import { GameSocketService, GameSocketStatus } from '../../../core/services/game-socket.service';

type GamePhase =
  | 'connecting'
  | 'starting'
  | 'preparing'
  | 'playing'
  | 'revealed'
  | 'finished'
  | 'error';

const COMMAND_GUARD_MS = 400;

@Component({
  selector: 'app-game-room',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './game-room.component.html',
  styleUrl: './game-room.component.scss',
})
export class GameRoomComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly gameSessions = inject(GameSessionService);
  private readonly gameSocket = inject(GameSocketService);
  private readonly audioPlayback = inject(AudioPlaybackService);

  @ViewChild('answerInput') private answerInput?: ElementRef<HTMLInputElement>;

  session: ActiveSoloSession | null = null;

  phase: GamePhase = 'connecting';
  roundNumber = 0;
  totalRounds = 0;
  secondsLeft = 0;
  totalScore = 0;
  lastRoundPoints: number | null = null;
  outcome: RoundOutcome | null = null;
  finished: GameFinishedPayload | null = null;
  reveal: Pick<CurrentRoundProjection, 'title' | 'artist' | 'composer' | 'album'> | null = null;
  advancing = false;
  private advanceAtMs: number | null = null;

  get isLastRound(): boolean {
    return this.roundNumber === this.totalRounds;
  }

  get canAdvance(): boolean {
    return this.phase === 'revealed' && this.socketStatus === 'connected' && !this.advancing;
  }

  advance(): void {
    if (!this.canAdvance) return;
    this.advancing = true;
    this.gameSocket.advanceRound();
  }

  feedback = '';
  discoveries: Partial<Record<SecondaryAnswerType, string | null>> = {};
  readonly secondaryTypes: SecondaryAnswerType[] = ['ARTIST', 'COMPOSER', 'ALBUM'];
  readonly secondaryLabels = { ARTIST: 'Artist', COMPOSER: 'Composer', ALBUM: 'Album' };
  cooldownSeconds = 0;
  private retryAtMs = 0;

  answer = '';
  sending = false;
  skipping = false;

  socketStatus: GameSocketStatus = 'idle';
  audioStatus: AudioPlaybackStatus = 'idle';
  audioError: string | null = null;
  gameError: string | null = null;
  announcement = '';

  private roundStartMs: number | null = null;
  private deadlineMs: number | null = null;
  private startRequested = false;
  private snapshotRequested = false;

  ngOnInit(): void {
    const sessionId = this.route.snapshot.paramMap.get('sessionId');
    this.session = sessionId ? this.gameSessions.getActiveSession(sessionId) : null;
    if (!this.session) {
      void this.router.navigate(['/lobby/create']);
      return;
    }

    this.totalRounds = this.session.trackCount;
    this.gameSocket.connect(this.session);

    this.gameSocket.status$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((status) => {
      this.socketStatus = status;
      if (status === 'connected' && !this.snapshotRequested) {
        this.snapshotRequested = true;
        this.gameSocket.requestSnapshot();
      }
    });

    this.gameSocket.snapshots$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((snapshot) => this.applySnapshot(snapshot));

    this.gameSocket.publicEvents$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.applyPublic(event));

    this.gameSocket.privateEvents$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.applyPrivate(event));

    this.gameSocket.fatalError$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) => this.applyFatalError(message));

    this.audioPlayback.status$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => (this.audioStatus = status));

    this.audioPlayback.error$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) => (this.audioError = message));

    timer(0, 250)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.tick());
  }

  ngOnDestroy(): void {
    this.gameSocket.disconnect();
    this.audioPlayback.stop();
  }

  get view(): 'setup' | 'error' | 'finished' | 'play' {
    if (this.phase === 'error') {
      return 'error';
    }
    if (this.phase === 'finished') {
      return 'finished';
    }
    return this.phase === 'connecting' || this.phase === 'starting' ? 'setup' : 'play';
  }

  get canSubmit(): boolean {
    return (
      this.phase === 'playing' &&
      this.socketStatus === 'connected' &&
      !this.sending &&
      Date.now() >= this.retryAtMs
    );
  }

  get canSkip(): boolean {
    return this.phase === 'playing' && this.socketStatus !== 'reconnecting' && !this.skipping;
  }

  get progressPct(): number {
    if (this.roundStartMs === null || this.deadlineMs === null) {
      return 0;
    }
    const total = this.deadlineMs - this.roundStartMs;
    if (total <= 0) {
      return 100;
    }
    return Math.min(100, Math.max(0, ((Date.now() - this.roundStartMs) / total) * 100));
  }

  get stateLabel(): string {
    if (this.phase === 'revealed') {
      return 'Revealed';
    }
    return this.phase === 'preparing' ? 'Next' : 'Playing';
  }

  get outcomeLabel(): string {
    switch (this.outcome) {
      case 'TITLE_FOUND':
        return 'You found the title';
      case 'SKIPPED':
        return 'Round skipped';
      case 'TIMEOUT':
        return 'Time ran out';
      default:
        return 'Round over';
    }
  }

  submit(): void {
    const value = this.answer.trim();
    if (!value || !this.canSubmit) {
      return;
    }
    this.sending = true;
    this.feedback = '';
    this.gameSocket.submitAnswer(value);
    this.answer = '';
    this.focusInput();
    window.setTimeout(() => (this.sending = false), COMMAND_GUARD_MS);
  }

  skip(): void {
    if (!this.canSkip) {
      return;
    }
    this.skipping = true;
    this.gameSocket.skipRound();
    window.setTimeout(() => (this.skipping = false), COMMAND_GUARD_MS);
  }

  resumeAudio(): void {
    this.audioPlayback.resume();
  }

  dismissError(): void {
    this.gameError = null;
  }

  playAgain(): void {
    void this.router.navigate(['/lobby/create']);
  }

  private tick(): void {
    this.cooldownSeconds = Math.max(0, Math.ceil((this.retryAtMs - Date.now()) / 1000));
    this.secondsLeft =
      this.phase === 'revealed'
        ? Math.max(0, Math.ceil(((this.advanceAtMs ?? Date.now()) - Date.now()) / 1000))
        : this.deadlineMs === null
          ? 0
          : Math.max(0, Math.ceil((this.deadlineMs - Date.now()) / 1000));
  }

  private applySnapshot(snapshot: CurrentSnapshotPayload): void {
    this.resetFeedback();
    this.clearReveal();
    this.deadlineMs = null;
    this.roundStartMs = null;
    this.audioPlayback.stop();
    this.totalRounds = snapshot.totalRounds;
    this.totalScore = snapshot.totalScore;
    this.roundNumber = snapshot.currentRoundIndex + 1;

    switch (snapshot.gameStatus) {
      case 'CREATED':
        if (!this.startRequested) {
          this.startRequested = true;
          this.phase = 'starting';
          this.gameSocket.startGame();
        }
        return;
      case 'FINISHED':
        this.clearReveal();
        this.phase = 'finished';
        this.audioPlayback.stop();
        return;
      case 'ABORTED':
        this.gameError = 'This game has ended.';
        this.phase = 'error';
        this.audioPlayback.stop();
        return;
      case 'PLAYING': {
        this.startRequested = true;
        const round = snapshot.currentRound;
        if (!round) {
          this.phase = 'preparing';
          return;
        }
        if (round.status === 'PLAYING') {
          this.enterRound({
            startsAt: round.startsAt ?? new Date().toISOString(),
            deadline: round.deadline ?? new Date().toISOString(),
            playbackUrl: round.playbackUrl,
            excerptStartSeconds: round.excerptStartSeconds ?? 0,
          });
          for (const type of snapshot.discoveredAnswerTypes) {
            this.discoveries[type] = null;
          }
        } else if (round.status === 'PREPARING') {
          this.phase = 'preparing';
        } else {
          this.enterReveal(round, round.outcome, round.roundPoints, round.advanceAt);
        }
        return;
      }
      default:
        return;
    }
  }

  private applyPublic(event: PublicSessionEvent): void {
    switch (event.type) {
      case 'SESSION_READY':
        this.totalRounds = event.payload.totalRounds;
        return;
      case 'ROUND_PREPARING':
        this.resetFeedback();
        this.clearReveal();
        this.phase = 'preparing';
        this.roundNumber = event.payload.roundIndex + 1;
        this.totalRounds = event.payload.totalRounds;
        this.deadlineMs = null;
        this.roundStartMs = null;
        this.lastRoundPoints = null;
        this.outcome = null;
        this.audioPlayback.stop();
        return;
      case 'ROUND_STARTED':
        this.roundNumber = event.payload.roundIndex + 1;
        this.totalRounds = event.payload.totalRounds;
        this.enterRound(event.payload);
        this.announcement = `Round ${this.roundNumber} of ${this.totalRounds} started.`;
        return;
      case 'ROUND_REVEALED':
        this.roundNumber = event.payload.roundIndex + 1;
        this.totalRounds = event.payload.totalRounds;
        this.totalScore = event.payload.totalScore;
        this.enterReveal(
          event.payload,
          event.payload.outcome,
          event.payload.roundPoints,
          event.payload.transitionAt,
        );
        return;
      case 'SCORE_UPDATED':
        this.totalScore = event.payload.totalScore;
        this.lastRoundPoints = event.payload.roundPoints;
        return;
      case 'GAME_FINISHED':
        this.resetFeedback();
        this.clearReveal();
        this.phase = 'finished';
        this.finished = event.payload;
        this.totalScore = event.payload.totalScore;
        this.audioPlayback.stop();
        this.announcement = 'Game finished.';
        return;
    }
  }

  private applyPrivate(event: PrivateSessionEvent): void {
    switch (event.type) {
      case 'ANSWER_REJECTED':
        if (this.phase === 'revealed') {
          this.advancing = false;
          this.gameSocket.requestSnapshot();
          return;
        }
        if (this.phase !== 'playing') return;
        this.sending = false;
        switch (event.payload.reason) {
          case 'WRONG_ANSWER':
            this.feedback = 'Not a match. Keep trying!';
            break;
          case 'ALREADY_FOUND':
            this.feedback = 'Already found. Keep guessing the title.';
            break;
          case 'COOLDOWN': {
            const retryAt = Date.parse(event.payload.retryAt ?? '');
            this.retryAtMs = Number.isFinite(retryAt) ? retryAt : 0;
            this.tick();
            this.feedback = 'You are answering too quickly. Try again shortly.';
            break;
          }
          case 'STALE_SESSION_VERSION':
            this.feedback = 'The game has moved on. Refreshing the round…';
            this.gameSocket.requestSnapshot();
            break;
          case 'INVALID_ROUND_STATE':
            this.feedback = 'Answers are not accepted right now. Refreshing the round…';
            this.gameSocket.requestSnapshot();
            break;
          case 'DUPLICATE_COMMAND':
            this.feedback = 'That attempt was already received.';
            break;
        }
        this.focusInput();
        return;
      case 'SECONDARY_MATCH': {
        if (this.phase !== 'playing') return;
        this.sending = false;
        const { answerType, matchedValue, alreadyFound } = event.payload;
        this.discoveries = { ...this.discoveries, [answerType]: matchedValue };
        this.feedback = `${this.secondaryLabels[answerType]} ${alreadyFound ? 'already found' : 'found'}: ${matchedValue}. Keep guessing the title.`;
        this.focusInput();
        return;
      }
      case 'ERROR':
        this.advancing = false;
        this.gameError = event.payload.message;
        if (!event.payload.recoverable) {
          this.phase = 'error';
          this.audioPlayback.stop();
        }
        return;
      case 'CURRENT_SNAPSHOT':
        return;
    }
  }

  private applyFatalError(message: string): void {
    this.gameError = message;
    this.phase = 'error';
    this.audioPlayback.stop();
  }

  private enterRound(payload: Omit<RoundStartedPayload, 'roundIndex' | 'totalRounds'>): void {
    this.resetFeedback();
    this.clearReveal();
    this.phase = 'playing';
    this.roundStartMs = Date.parse(payload.startsAt);
    this.deadlineMs = Date.parse(payload.deadline);
    this.answer = '';
    this.sending = false;
    this.skipping = false;
    this.gameError = null;
    this.tick();
    this.focusInput();

    if (payload.playbackUrl) {
      this.audioPlayback.scheduleRound({
        url: payload.playbackUrl,
        startsAt: payload.startsAt,
        deadline: payload.deadline,
        excerptStartSeconds: payload.excerptStartSeconds,
      });
    }
  }

  private clearReveal(): void {
    this.reveal = null;
    this.advanceAtMs = null;
    this.advancing = false;
    this.lastRoundPoints = null;
    this.outcome = null;
  }

  private enterReveal(
    metadata: NonNullable<GameRoomComponent['reveal']>,
    outcome: RoundOutcome | null,
    points: number,
    advanceAt: string | null,
  ): void {
    this.resetFeedback();
    this.phase = 'revealed';
    this.reveal = metadata;
    this.outcome = outcome;
    this.lastRoundPoints = points;
    this.advanceAtMs = advanceAt ? Date.parse(advanceAt) : null;
    this.deadlineMs = null;
    this.roundStartMs = null;
    this.sending = false;
    this.skipping = false;
    this.advancing = false;
    this.answer = '';
    this.audioPlayback.stop();
    this.tick();
    this.announcement = `${this.outcomeLabel}. ${metadata.title ?? 'Round over'}. ${points} points.`;
  }

  private resetFeedback(): void {
    this.feedback = '';
    this.discoveries = {};
    this.retryAtMs = 0;
    this.cooldownSeconds = 0;
  }

  private focusInput(): void {
    window.setTimeout(() => this.answerInput?.nativeElement.focus(), 0);
  }
}
