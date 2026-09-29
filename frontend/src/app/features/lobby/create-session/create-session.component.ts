import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { GameSessionService } from '../../../core/services/game-session.service';
import { PublicPlaylist, PublicPlaylistDetail } from './catalogue.model';
import { CatalogueService } from './catalogue.service';
import {
  GAME_CONFIGURATION_LIMITS,
  isAllowedRoundDuration,
  isAllowedTrackCount,
} from './game-configuration.model';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-create-session',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './create-session.component.html',
  styleUrl: './create-session.component.scss',
})
export class CreateSessionComponent implements OnInit {
  private readonly catalogue = inject(CatalogueService);
  private readonly gameSessions = inject(GameSessionService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);
  readonly auth = inject(AuthService);
  readonly limits = GAME_CONFIGURATION_LIMITS;

  readonly form = this.formBuilder.nonNullable.group({
    playlistId: ['', Validators.required],
    trackCount: [
      Number(this.limits.trackCount.defaultValue),
      this.trackCountValidators(this.limits.trackCount.maximum),
    ],
    maxRoundSeconds: [
      Number(this.limits.roundDurationSeconds.defaultValue),
      [
        Validators.required,
        Validators.min(this.limits.roundDurationSeconds.minimum),
        Validators.max(this.limits.roundDurationSeconds.maximum),
        (control: AbstractControl<number>) =>
          isAllowedRoundDuration(control.value) ? null : { roundDurationIncrement: true },
      ],
    ],
  });

  playlists: PublicPlaylist[] = [];
  selected: PublicPlaylistDetail | null = null;
  loading = true;
  loadingDetail = false;
  submitting = false;
  errorMessage = '';
  detailError = '';
  submitError = '';

  ngOnInit(): void {
    this.loadPlaylists();
  }

  loadPlaylists(): void {
    this.loading = true;
    this.errorMessage = '';
    this.catalogue
      .playlists()
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (playlists) => {
          this.playlists = playlists.filter(
            (playlist) => playlist.trackCount >= this.limits.trackCount.minimum,
          );
          const selectedId = this.form.controls.playlistId.value;
          const preferred =
            this.playlists.find((playlist) => playlist.id === selectedId) ?? this.playlists[0];
          if (preferred) {
            this.selectPlaylist(preferred);
          } else {
            this.selected = null;
            this.form.controls.playlistId.setValue('');
            this.setTrackCountMaximum(this.limits.trackCount.maximum);
          }
        },
        error: (error) => (this.errorMessage = this.errorText(error)),
      });
  }

  selectPlaylist(playlist: PublicPlaylist): void {
    if (this.selected?.id === playlist.id && !this.detailError) {
      return;
    }
    this.form.controls.playlistId.setValue(playlist.id);
    this.loadingDetail = true;
    this.detailError = '';
    this.submitError = '';
    this.catalogue
      .playlist(playlist.id)
      .pipe(finalize(() => (this.loadingDetail = false)))
      .subscribe({
        next: (detail) => {
          if (detail.trackCount < this.limits.trackCount.minimum) {
            this.selected = null;
            this.detailError = 'This playlist no longer contains enough published tracks.';
            this.setTrackCountMaximum(this.limits.trackCount.maximum);
            return;
          }
          this.selected = detail;
          const maximum = Math.min(detail.trackCount, this.limits.trackCount.maximum);
          this.setTrackCountMaximum(maximum);
          const currentCount = this.form.controls.trackCount.value;
          this.form.controls.trackCount.setValue(
            Math.min(Math.max(currentCount, this.limits.trackCount.minimum), maximum),
          );
        },
        error: (error) => {
          this.selected = null;
          this.detailError = this.errorText(error);
          this.setTrackCountMaximum(this.limits.trackCount.maximum);
        },
      });
  }

  createSession(): void {
    if (this.form.invalid || !this.selected) {
      this.form.markAllAsTouched();
      return;
    }
    const { playlistId, trackCount, maxRoundSeconds } = this.form.getRawValue();
    const playlistName = this.selected.name;
    this.submitting = true;
    this.submitError = '';
    this.gameSessions
      .createSolo({ playlistId, trackCount, maxRoundSeconds, extensions: {} })
      .pipe(finalize(() => (this.submitting = false)))
      .subscribe({
        next: (session) => {
          this.gameSessions.setActiveSession({
            ...session,
            playlistName,
            trackCount,
            maxRoundSeconds,
          });
          void this.router.navigate(['/game', session.sessionId]);
        },
        error: (error) => this.handleCreateError(error, trackCount),
      });
  }

  private handleCreateError(error: unknown, trackCount: number): void {
    this.submitError = this.describeCreateError(error, trackCount);
    if (error instanceof HttpErrorResponse) {
      const code = error.error?.code as string | undefined;
      if (
        code === 'GAME_PLAYLIST_NOT_FOUND' ||
        code === 'GAME_PLAYLIST_INELIGIBLE' ||
        code === 'GAME_PLAYLIST_INSUFFICIENT_TRACKS'
      ) {
        this.loadPlaylists();
      }
    }
  }

  private describeCreateError(error: unknown, trackCount: number): string {
    if (error instanceof HttpErrorResponse) {
      const code = error.error?.code as string | undefined;
      const message = error.error?.message as string | undefined;
      switch (code) {
        case 'GAME_PLAYLIST_REQUIRED':
          return 'Choose a playlist before starting a game.';
        case 'GAME_TRACK_COUNT_INVALID':
          return 'The track count is outside the allowed range.';
        case 'GAME_ROUND_DURATION_INVALID':
          return 'The answer time is outside the allowed range.';
        case 'GAME_CONFIGURATION_EXTENSION_UNSUPPORTED':
          return "That game option isn't available yet.";
        case 'GAME_PLAYLIST_NOT_FOUND':
          return 'That playlist is no longer available. The list has been refreshed.';
        case 'GAME_PLAYLIST_INELIGIBLE':
          return 'This playlist has no tracks ready to play right now.';
        case 'GAME_PLAYLIST_INSUFFICIENT_TRACKS':
          return (
            `This playlist doesn't have enough ready tracks for ${trackCount} rounds. ` +
            'Pick fewer tracks or another playlist.'
          );
        case 'EMAIL_NOT_VERIFIED':
          return 'Verify your email address before starting a game.';
        default:
          break;
      }
      if (error.status === 0) {
        return 'The server can’t be reached. Check your connection and try again.';
      }
      return message || 'The game could not be created. Please try again.';
    }
    return 'The game could not be created. Please try again.';
  }

  estimatedMinutes(): number {
    const { trackCount, maxRoundSeconds } = this.form.getRawValue();
    return Math.max(1, Math.round((trackCount * (maxRoundSeconds + 8)) / 60));
  }

  trackCountMaximum(): number {
    return Math.min(
      this.selected?.trackCount ?? this.limits.trackCount.maximum,
      this.limits.trackCount.maximum,
    );
  }

  formatDuration(seconds: number | null): string {
    if (!seconds) {
      return '—';
    }
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  }

  private errorText(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.message || error.message || 'The catalogue could not be loaded.';
    }
    return 'The catalogue could not be loaded.';
  }

  private setTrackCountMaximum(maximum: number): void {
    this.form.controls.trackCount.setValidators(this.trackCountValidators(maximum));
    this.form.controls.trackCount.updateValueAndValidity({ emitEvent: false });
  }

  private trackCountValidators(maximum: number) {
    return [
      Validators.required,
      Validators.min(this.limits.trackCount.minimum),
      Validators.max(maximum),
      (control: AbstractControl<number>) =>
        isAllowedTrackCount(control.value) ? null : { trackCountIncrement: true },
    ];
  }
}
