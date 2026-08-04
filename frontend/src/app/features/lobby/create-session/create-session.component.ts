import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { PublicPlaylist, PublicPlaylistDetail } from './catalogue.model';
import { CatalogueService } from './catalogue.service';
import {
  GAME_CONFIGURATION_LIMITS,
  isAllowedRoundDuration,
  isAllowedTrackCount,
  PendingGameConfiguration,
} from './game-configuration.model';

const PENDING_GAME_CONFIGURATION_KEY = 'pending_game_configuration';

@Component({
  selector: 'app-create-session',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './create-session.component.html',
  styleUrl: './create-session.component.scss',
})
export class CreateSessionComponent implements OnInit {
  private readonly catalogue = inject(CatalogueService);
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
  errorMessage = '';
  detailError = '';
  readyMessage = '';

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
    this.readyMessage = '';
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

  prepareSession(): void {
    if (this.form.invalid || !this.selected) {
      this.form.markAllAsTouched();
      return;
    }
    const values = this.form.getRawValue();
    const configuration: PendingGameConfiguration = { ...values, extensions: {} };
    sessionStorage.setItem(PENDING_GAME_CONFIGURATION_KEY, JSON.stringify(configuration));
    this.readyMessage =
      'Configuration saved. Session creation will become available with the gameplay service.';
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
