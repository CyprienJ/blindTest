import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, finalize, merge, Subscription } from 'rxjs';
import { AdminTrack } from '../track-list/admin-track.model';
import { AdminTrackService } from '../track-list/admin-track.service';

/**
 * Searches the published catalogue for tracks outside a playlist, with category, decade, and
 * difficulty filters, paging, and an excerpt preview. The parent performs the actual add.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-playlist-track-picker',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './playlist-track-picker.component.html',
  styleUrl: './playlist-track-picker.component.scss',
})
export class PlaylistTrackPickerComponent implements OnInit, OnChanges {
  readonly stopPreviewLabel = $localize`Stop preview`;
  readonly previewExcerptLabel = $localize`Preview excerpt`;

  stopPreviewOf(title: string): string {
    return $localize`Stop preview of ${title}:title:`;
  }

  difficultyLabel(difficulty: number): string {
    return $localize`Difficulty ${difficulty}:difficulty: of 5`;
  }

  addLabel(title: string): string {
    return $localize`Add ${title}:title:`;
  }

  previewOf(title: string): string {
    return $localize`Preview ${title}:title:`;
  }

  private readonly tracksService = inject(AdminTrackService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  /** Playlist whose tracks are excluded server-side; null while none is selected. */
  @Input() playlistId: string | null = null;
  /** Current members, hidden at once after an add without reloading. */
  @Input() memberIds: string[] = [];
  @Input() canAdd = false;
  @Input() addingTrackId: string | null = null;
  @Output() readonly add = new EventEmitter<AdminTrack>();

  readonly searchControl = this.formBuilder.nonNullable.control('');
  readonly filters = this.formBuilder.nonNullable.group({
    category: [''],
    decade: [''],
    difficulty: [''],
  });
  readonly decades = Array.from({ length: 8 }, (_, index) => 1950 + index * 10);
  readonly difficulties = [1, 2, 3, 4, 5];
  readonly pageSize = 20;

  categories: string[] = [];
  tracks: AdminTrack[] = [];
  total = 0;
  loading = false;
  loadingMore = false;
  error = '';
  previewTrackId: string | null = null;
  previewLoadingId: string | null = null;
  previewError = '';
  private page = 0;
  private request: Subscription | null = null;
  private audio: HTMLAudioElement | null = null;

  ngOnInit(): void {
    merge(
      this.searchControl.valueChanges.pipe(debounceTime(250), distinctUntilChanged()),
      this.filters.valueChanges,
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload());
    this.destroyRef.onDestroy(() => {
      this.request?.unsubscribe();
      this.stopPreview();
    });
    this.tracksService
      .categories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (categories) => (this.categories = categories), error: () => undefined });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['playlistId']) {
      this.reload();
    }
  }

  /** Reloads the first page, for example after a track left the playlist. */
  reload(): void {
    this.fetch(0);
  }

  loadMore(): void {
    if (!this.loading && !this.loadingMore && this.hasMore) {
      this.fetch(this.page + 1);
    }
  }

  get visibleTracks(): AdminTrack[] {
    return this.tracks.filter((track) => !this.memberIds.includes(track.id));
  }

  /** Matching tracks still outside the playlist, including the ones not loaded yet. */
  get availableCount(): number {
    return this.total - (this.tracks.length - this.visibleTracks.length);
  }

  get hasMore(): boolean {
    return this.tracks.length < this.total;
  }

  difficultyStars(difficulty: number | null): string {
    return difficulty ? '★'.repeat(difficulty) + '☆'.repeat(5 - difficulty) : '';
  }

  formatDuration(seconds: number | null): string {
    if (!seconds) {
      return '—';
    }
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  }

  /** Plays the track's game excerpt, or stops it when it is already playing. */
  togglePreview(track: AdminTrack): void {
    if (this.previewTrackId === track.id || this.previewLoadingId === track.id) {
      this.stopPreview();
      return;
    }
    this.stopPreview();
    this.previewError = '';
    this.previewLoadingId = track.id;
    this.tracksService.preview(track.id).subscribe({
      next: (preview) => {
        if (this.previewLoadingId === track.id) {
          this.previewLoadingId = null;
          this.play(track, preview.url);
        }
      },
      error: (error) => {
        this.previewLoadingId = null;
        this.previewError = this.errorMessage(error);
      },
    });
  }

  stopPreview(): void {
    this.previewLoadingId = null;
    this.previewTrackId = null;
    this.audio?.pause();
  }

  private fetch(page: number): void {
    const reset = page === 0;
    this.request?.unsubscribe();
    this.loading = reset;
    this.loadingMore = !reset;
    this.error = '';
    const filters = this.filters.getRawValue();
    this.request = this.tracksService
      .list(page, this.pageSize, 'PUBLISHED', this.searchControl.value, {
        category: filters.category || null,
        decade: filters.decade ? Number(filters.decade) : null,
        difficulty: filters.difficulty ? Number(filters.difficulty) : null,
        excludePlaylistId: this.playlistId,
      })
      .pipe(
        finalize(() => {
          this.loading = false;
          this.loadingMore = false;
        }),
      )
      .subscribe({
        next: (response) => {
          const known = new Set(reset ? [] : this.tracks.map((track) => track.id));
          this.tracks = [
            ...(reset ? [] : this.tracks),
            ...response.content.filter((track) => !known.has(track.id)),
          ];
          this.total = response.totalElements;
          this.page = page;
        },
        error: (error) => (this.error = this.errorMessage(error)),
      });
  }

  private play(track: AdminTrack, url: string): void {
    const audio = (this.audio ??= new Audio());
    const start = track.excerptStartSeconds;
    // A media fragment starts at the excerpt and pauses the audio at the window's end.
    audio.src = `${url}#t=${start},${start + track.excerptDurationSeconds}`;
    audio.onpause = () => {
      if (this.previewTrackId === track.id) {
        this.previewTrackId = null;
      }
    };
    this.previewTrackId = track.id;
    audio.play().catch(() => {
      if (this.previewTrackId === track.id) {
        this.previewTrackId = null;
        this.previewError = $localize`The browser blocked playback. Press play again.`;
      }
    });
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.message || $localize`The request could not be completed.`;
    }
    return $localize`The request could not be completed.`;
  }
}
