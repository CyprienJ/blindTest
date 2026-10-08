import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  OnInit,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { GameSummary, GameSummaryRound } from '../../core/models/game-summary.model';
import { RoundOutcome } from '../../core/models/game-protocol.model';
import { GameSessionService } from '../../core/services/game-session.service';
import { GuestSessionService } from '../../core/services/guest-session.service';
import { LobbyService } from '../../core/services/lobby.service';

type EndScreenView = 'loading' | 'error' | 'ready';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-end-screen',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './end-screen.component.html',
  styleUrl: './end-screen.component.scss',
})
export class EndScreenComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gameSessions = inject(GameSessionService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly guestSession = inject(GuestSessionService);
  private readonly lobbies = inject(LobbyService);

  view: EndScreenView = 'loading';
  summary: GameSummary | null = null;
  errorMessage = '';
  returning = false;
  rematchError = '';

  ngOnInit(): void {
    const sessionId = this.route.snapshot.paramMap.get('sessionId');
    if (!sessionId) {
      this.view = 'error';
      this.errorMessage = $localize`This link is missing a game to summarize.`;
      this.changeDetector.markForCheck();
      return;
    }

    this.gameSessions.getSummary(sessionId).subscribe({
      next: (summary) => {
        this.summary = summary;
        this.view = 'ready';
        this.changeDetector.markForCheck();
      },
      error: (response: HttpErrorResponse) => {
        this.errorMessage =
          response.status === 404
            ? $localize`We couldn't find a summary for this game.`
            : $localize`Something went wrong loading this summary.`;
        this.view = 'error';
        this.changeDetector.markForCheck();
      },
    });
  }

  /** Guests have no history or account screens; they can only join another game. */
  get guest(): boolean {
    return this.guestSession.get() !== null;
  }

  /**
   * Returns to the finished game's lobby with the same code and settings (ADR-0023). The first
   * player back reopens it; guests switch to the token for the reopened lobby.
   */
  backToLobby(): void {
    const summary = this.summary;
    if (!summary || this.returning) {
      return;
    }
    this.returning = true;
    this.rematchError = '';
    this.lobbies
      .rematch(summary.sessionId)
      .pipe(
        finalize(() => {
          this.returning = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: ({ guestToken, lobby }) => {
          const guest = this.guestSession.get();
          if (guest && guestToken) {
            this.guestSession.set({
              ...guest,
              token: guestToken,
              code: lobby.code,
              sessionId: lobby.sessionId,
              participantId: lobby.participantId,
            });
          }
          void this.router.navigate(['/lobby', lobby.code]);
        },
        error: (error: unknown) => (this.rematchError = this.describeRematchError(error)),
      });
  }

  playAgain(): void {
    if (this.guest) {
      this.guestSession.clear();
      void this.router.navigate(['/lobby/join']);
      return;
    }
    void this.router.navigate(['/lobby/create']);
  }

  private describeRematchError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.error?.code) {
        case 'LOBBY_NOT_FOUND':
          return $localize`This game's lobby has closed. Start or join a new game instead.`;
        case 'LOBBY_FULL':
          return $localize`The lobby is full now.`;
        case 'GUEST_NICKNAME_TAKEN':
          return $localize`Another player in the lobby now uses your nickname.`;
        case 'LOBBY_GAME_IN_PROGRESS':
          return $localize`The game is still being played.`;
        default:
          break;
      }
      if (error.status === 0) {
        return $localize`The server can’t be reached. Check your connection and try again.`;
      }
    }
    return $localize`Something went wrong. Please try again.`;
  }

  outcomeLabel(outcome: RoundOutcome): string {
    switch (outcome) {
      case 'TITLE_FOUND':
        return $localize`Found`;
      case 'TIMEOUT':
        return $localize`Timed out`;
      case 'SKIPPED':
        return $localize`Skipped`;
    }
  }

  responseTimeLabel(round: GameSummaryRound): string {
    const startedMs = Date.parse(round.startedAt);
    const revealedMs = Date.parse(round.revealedAt);
    if (!Number.isFinite(startedMs) || !Number.isFinite(revealedMs) || revealedMs < startedMs) {
      return '—';
    }
    return `${((revealedMs - startedMs) / 1000).toFixed(1)}s`;
  }

  get durationLabel(): string {
    if (!this.summary) {
      return '';
    }
    const startedMs = Date.parse(this.summary.startedAt);
    const completedMs = Date.parse(this.summary.completedAt);
    if (!Number.isFinite(startedMs) || !Number.isFinite(completedMs) || completedMs < startedMs) {
      return '';
    }
    const totalSeconds = Math.round((completedMs - startedMs) / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
  }
}
