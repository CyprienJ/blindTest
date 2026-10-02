import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  OnInit,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { GameSummary, GameSummaryRound } from '../../core/models/game-summary.model';
import { RoundOutcome } from '../../core/models/game-protocol.model';
import { GameSessionService } from '../../core/services/game-session.service';

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

  view: EndScreenView = 'loading';
  summary: GameSummary | null = null;
  errorMessage = '';

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

  playAgain(): void {
    void this.router.navigate(['/lobby/create']);
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
