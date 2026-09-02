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
  GameFinishedPayload,
  PrivateSessionEvent,
  PublicSessionEvent,
  RoundOutcome,
  RoundStartedPayload,
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
    return this.phase === 'playing' && this.socketStatus !== 'reconnecting' && !this.sending;
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
    this.secondsLeft =
      this.deadlineMs === null ? 0 : Math.max(0, Math.ceil((this.deadlineMs - Date.now()) / 1000));
  }

  private applySnapshot(snapshot: CurrentSnapshotPayload): void {
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
        } else if (round.status === 'PREPARING') {
          this.phase = 'preparing';
        } else {
          this.phase = 'revealed';
          this.audioPlayback.stop();
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
        this.phase = 'revealed';
        this.sending = false;
        this.outcome = event.payload.outcome;
        this.lastRoundPoints = event.payload.roundPoints;
        this.totalScore = event.payload.totalScore;
        this.deadlineMs = null;
        this.roundStartMs = null;
        this.audioPlayback.stop();
        this.announcement = 'Round over.';
        return;
      case 'SCORE_UPDATED':
        this.totalScore = event.payload.totalScore;
        this.lastRoundPoints = event.payload.roundPoints;
        return;
      case 'GAME_FINISHED':
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
      case 'SECONDARY_MATCH':
        this.sending = false;
        return;
      case 'ERROR':
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

  private enterRound(payload: Omit<RoundStartedPayload, 'roundIndex' | 'totalRounds'>): void {
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

  private focusInput(): void {
    window.setTimeout(() => this.answerInput?.nativeElement.focus(), 0);
  }
}
