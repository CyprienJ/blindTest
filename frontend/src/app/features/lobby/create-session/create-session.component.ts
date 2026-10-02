import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { CreateSoloSessionRequest } from '../../../core/models/game-session.model';
import { GameSessionService } from '../../../core/services/game-session.service';
import { LobbyService } from '../../../core/services/lobby.service';
import { PublicPlaylist, PublicPlaylistDetail } from './catalogue.model';
import { CatalogueService } from './catalogue.service';
import {
  DIFFICULTY_LEVELS,
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
  private readonly lobbies = inject(LobbyService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  readonly auth = inject(AuthService);
  readonly limits = GAME_CONFIGURATION_LIMITS;
  readonly difficultyLevels = DIFFICULTY_LEVELS;
  readonly selectedLevels = new Set<number>(DIFFICULTY_LEVELS);
  includeUnrated = true;
  /** Solo plays at once; multiplayer opens a lobby that friends join by code. */
  mode: 'solo' | 'multiplayer' =
    this.route.snapshot.queryParamMap.get('mode') === 'multiplayer' ? 'multiplayer' : 'solo';

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

  selectPlaylist(playlist: PublicPlaylist, refresh = false): void {
    if (this.selected?.id === playlist.id && !this.detailError && !refresh) {
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
          this.applyTrackCountMaximum();
        },
        error: (error) => {
          this.selected = null;
          this.detailError = this.errorText(error);
          this.setTrackCountMaximum(this.limits.trackCount.maximum);
        },
      });
  }

  toggleDifficulty(level: number | null): void {
    if (level === null) {
      this.includeUnrated = !this.includeUnrated;
    } else if (!this.selectedLevels.delete(level)) {
      this.selectedLevels.add(level);
    }
    this.submitError = '';
    this.applyTrackCountMaximum();
  }

  isDifficultySelected(level: number | null): boolean {
    return level === null ? this.includeUnrated : this.selectedLevels.has(level);
  }

  /** Counts tracks of the selected playlist rated at the level, or unrated for null. */
  difficultyCount(level: number | null): number {
    return (
      this.selected?.tracks.filter((track) => (track.difficulty ?? null) === level).length ?? 0
    );
  }

  difficultyLabel(level: number | null): string {
    const name = level === null ? 'Not rated' : `${level} ${level === 1 ? 'star' : 'stars'}`;
    const count = this.difficultyCount(level);
    return `${name}, ${count} ${count === 1 ? 'track' : 'tracks'}`;
  }

  matchingTrackCount(): number {
    return (
      this.selected?.tracks.filter((track) => this.isDifficultySelected(track.difficulty ?? null))
        .length ?? 0
    );
  }

  /** Explains why the difficulty selection cannot start a game, or returns an empty string. */
  difficultyError(): string {
    if (!this.selected) {
      return '';
    }
    if (this.selectedLevels.size === 0 && !this.includeUnrated) {
      return 'Select at least one difficulty level.';
    }
    const matching = this.matchingTrackCount();
    if (matching < this.limits.trackCount.minimum) {
      return (
        `Only ${matching} ${matching === 1 ? 'track matches' : 'tracks match'} these levels; ` +
        `a game needs at least ${this.limits.trackCount.minimum}. Include more levels.`
      );
    }
    return '';
  }

  difficultySummary(): string {
    const levels = [...this.selectedLevels].sort((a, b) => a - b);
    if (levels.length === this.difficultyLevels.length && this.includeUnrated) {
      return 'All levels';
    }
    const parts = levels.length > 0 ? [`${levels.join(', ')} ★`] : [];
    if (this.includeUnrated) {
      parts.push('not rated');
    }
    return parts.join(' · ') || 'None';
  }

  setMode(mode: 'solo' | 'multiplayer'): void {
    this.mode = mode;
    this.submitError = '';
  }

  createSession(): void {
    if (this.form.invalid || !this.selected || this.difficultyError()) {
      this.form.markAllAsTouched();
      return;
    }
    const { playlistId, trackCount, maxRoundSeconds } = this.form.getRawValue();
    const playlistName = this.selected.name;
    const request: CreateSoloSessionRequest = {
      playlistId,
      trackCount,
      maxRoundSeconds,
      extensions: {},
      difficultyLevels: [...this.selectedLevels].sort((a, b) => a - b),
      includeUnrated: this.includeUnrated,
    };
    this.submitting = true;
    this.submitError = '';
    if (this.mode === 'multiplayer') {
      this.lobbies
        .create(request)
        .pipe(finalize(() => (this.submitting = false)))
        .subscribe({
          next: (lobby) => void this.router.navigate(['/lobby', lobby.code]),
          error: (error) => this.handleCreateError(error, trackCount),
        });
      return;
    }
    this.gameSessions
      .createSolo(request)
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
      } else if (code === 'GAME_DIFFICULTY_INSUFFICIENT_TRACKS' && this.selected) {
        this.selectPlaylist(this.selected, true);
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
        case 'GAME_DIFFICULTY_INVALID':
          return 'Select at least one difficulty level.';
        case 'GAME_DIFFICULTY_INSUFFICIENT_TRACKS':
          return (
            `Not enough tracks match the selected difficulty levels for ${trackCount} rounds. ` +
            'Include more levels or pick fewer tracks.'
          );
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
      this.selected ? this.matchingTrackCount() : this.limits.trackCount.maximum,
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

  /** Caps the track count to the tracks matching the difficulty selection. */
  private applyTrackCountMaximum(): void {
    const maximum = Math.max(this.trackCountMaximum(), this.limits.trackCount.minimum);
    this.setTrackCountMaximum(maximum);
    const currentCount = this.form.controls.trackCount.value;
    this.form.controls.trackCount.setValue(
      Math.min(Math.max(currentCount, this.limits.trackCount.minimum), maximum),
    );
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
