import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { PublicPlaylist, PublicPlaylistDetail } from './catalogue.model';
import { CatalogueService } from './catalogue.service';

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

  readonly form = this.formBuilder.nonNullable.group({
    playlistId: ['', Validators.required],
    trackCount: [1, [Validators.required, Validators.min(1)]],
    maxRoundSeconds: [30, [Validators.required, Validators.min(10), Validators.max(120)]],
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
          this.playlists = playlists.filter((playlist) => playlist.trackCount > 0);
          const selectedId = this.form.controls.playlistId.value;
          const preferred =
            this.playlists.find((playlist) => playlist.id === selectedId) ?? this.playlists[0];
          if (preferred) {
            this.selectPlaylist(preferred);
          } else {
            this.selected = null;
            this.form.controls.playlistId.setValue('');
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
          this.selected = detail;
          const currentCount = this.form.controls.trackCount.value;
          this.form.controls.trackCount.setValue(
            Math.min(Math.max(currentCount, 1), detail.trackCount),
          );
        },
        error: (error) => {
          this.selected = null;
          this.detailError = this.errorText(error);
        },
      });
  }

  prepareSession(): void {
    if (this.form.invalid || !this.selected) {
      this.form.markAllAsTouched();
      return;
    }
    const configuration = this.form.getRawValue();
    sessionStorage.setItem(PENDING_GAME_CONFIGURATION_KEY, JSON.stringify(configuration));
    this.readyMessage =
      'Configuration saved. Session creation will become available with the gameplay service.';
  }

  estimatedMinutes(): number {
    const { trackCount, maxRoundSeconds } = this.form.getRawValue();
    return Math.max(1, Math.round((trackCount * (maxRoundSeconds + 8)) / 60));
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
}
