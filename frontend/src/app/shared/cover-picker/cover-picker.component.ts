import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, input, model, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  EMPTY,
  filter,
  switchMap,
  tap,
} from 'rxjs';
import {
  CoverQuery,
  CoverSource,
  CoverSuggestion,
  TrackUpdate,
} from '../../features/admin/track-list/admin-track.model';
import { AdminTrackService } from '../../features/admin/track-list/admin-track.service';

/** What the administrator chose for the track's cover; applied when the track is saved. */
export type CoverSelection =
  | { kind: 'keep' }
  | { kind: 'candidate'; candidateId: string }
  | { kind: 'remove' };

export const KEEP_COVER: CoverSelection = { kind: 'keep' };

/** Mirrors the server's 5 MB cover limit so oversized files fail before upload. */
const MAX_COVER_BYTES = 5 * 1024 * 1024;
const SEARCH_DEBOUNCE_MS = 700;
const SOURCE_LABELS: Record<CoverSource, string> = {
  DEEZER: 'Deezer',
  ITUNES: 'iTunes',
  COVER_ART_ARCHIVE: 'Cover Art Archive',
  UPLOAD: 'Upload',
};

/** Track-update fields for a cover selection; keeping the cover sends nothing. */
export function coverUpdate(
  selection: CoverSelection,
): Pick<TrackUpdate, 'coverCandidateId' | 'removeCover'> {
  if (selection.kind === 'candidate') {
    return { coverCandidateId: selection.candidateId };
  }
  return selection.kind === 'remove' ? { removeCover: true } : {};
}

interface CoverTile extends CoverSuggestion {
  /** Object URL of the thumbnail blob; null while it loads or when it failed. */
  thumbnailUrl: string | null;
}

/**
 * Suggests up to six covers from the track's title, artist and album, searching again
 * automatically when they change, and lets the administrator pick one, upload an image,
 * keep the current cover, or remove it.
 */
@Component({
  selector: 'app-cover-picker',
  standalone: true,
  templateUrl: './cover-picker.component.html',
  styleUrl: './cover-picker.component.scss',
})
export class CoverPickerComponent {
  private readonly service = inject(AdminTrackService);

  readonly title = input('');
  readonly artist = input('');
  readonly album = input<string | null>(null);
  /** Path of the stored cover, shown as the "keep" option. */
  readonly currentCoverUrl = input<string | null>(null);
  readonly disabled = input(false);
  readonly selection = model<CoverSelection>(KEEP_COVER);

  readonly tiles = signal<CoverTile[]>([]);
  readonly searchState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly uploading = signal(false);
  readonly uploadError = signal('');

  readonly maxUploadLabel = '5 MB';
  readonly noCoverSelected = computed(() => {
    const kind = this.selection().kind;
    return kind === 'remove' || (kind === 'keep' && !this.currentCoverUrl());
  });

  private readonly query = computed<CoverQuery>(() => ({
    title: this.title().trim(),
    artist: this.artist().trim(),
    album: this.album()?.trim() || null,
  }));
  private readonly objectUrls = new Set<string>();

  constructor() {
    toObservable(this.query)
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        filter((query) => query.title !== '' && query.artist !== ''),
        distinctUntilChanged(
          (a, b) => a.title === b.title && a.artist === b.artist && a.album === b.album,
        ),
        tap(() => this.searchState.set('loading')),
        switchMap((query) =>
          this.service.suggestCovers(query).pipe(
            catchError(() => {
              this.searchState.set('error');
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((suggestions) => this.showSuggestions(suggestions));
    inject(DestroyRef).onDestroy(() => this.objectUrls.forEach((url) => URL.revokeObjectURL(url)));
  }

  sourceLabel(source: CoverSource): string {
    return SOURCE_LABELS[source];
  }

  isSelected(tile: CoverTile): boolean {
    const selection = this.selection();
    return selection.kind === 'candidate' && selection.candidateId === tile.candidateId;
  }

  choose(tile: CoverTile): void {
    this.selection.set({ kind: 'candidate', candidateId: tile.candidateId });
  }

  keep(): void {
    this.selection.set(KEEP_COVER);
  }

  chooseNoCover(): void {
    this.selection.set(this.currentCoverUrl() ? { kind: 'remove' } : KEEP_COVER);
  }

  upload(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.uploading()) {
      return;
    }
    if (file.size > MAX_COVER_BYTES) {
      this.uploadError.set(`The image is larger than ${this.maxUploadLabel}.`);
      return;
    }
    this.uploading.set(true);
    this.uploadError.set('');
    this.service.uploadCover(file).subscribe({
      next: (suggestion) => {
        const tile = { ...suggestion, thumbnailUrl: this.objectUrl(file) };
        this.tiles.update((tiles) => [tile, ...tiles]);
        this.choose(tile);
        this.uploading.set(false);
      },
      error: (error: unknown) => {
        this.uploading.set(false);
        this.uploadError.set(this.uploadErrorMessage(error));
      },
    });
  }

  private showSuggestions(suggestions: CoverSuggestion[]): void {
    const selection = this.selection();
    const kept = this.tiles().filter(
      (tile) => selection.kind === 'candidate' && tile.candidateId === selection.candidateId,
    );
    this.tiles().forEach((tile) => {
      if (!kept.includes(tile) && tile.thumbnailUrl) {
        this.revoke(tile.thumbnailUrl);
      }
    });
    this.tiles.set([
      ...kept,
      ...suggestions.map((suggestion) => ({ ...suggestion, thumbnailUrl: null })),
    ]);
    this.searchState.set('ready');
    suggestions.forEach((suggestion) => this.loadThumbnail(suggestion.candidateId));
  }

  private loadThumbnail(candidateId: string): void {
    this.service.coverThumbnail(candidateId).subscribe({
      next: (blob) => {
        const url = this.objectUrl(blob);
        let used = false;
        this.tiles.update((tiles) =>
          tiles.map((tile) => {
            if (tile.candidateId !== candidateId) {
              return tile;
            }
            used = true;
            return { ...tile, thumbnailUrl: url };
          }),
        );
        if (!used) {
          this.revoke(url);
        }
      },
      // A failed thumbnail stays a placeholder; the cover can still be chosen.
      error: () => undefined,
    });
  }

  private objectUrl(blob: Blob): string {
    const url = URL.createObjectURL(blob);
    this.objectUrls.add(url);
    return url;
  }

  private revoke(url: string): void {
    URL.revokeObjectURL(url);
    this.objectUrls.delete(url);
  }

  private uploadErrorMessage(error: unknown): string {
    const code = error instanceof HttpErrorResponse ? error.error?.code : undefined;
    if (code === 'COVER_IMAGE_TOO_LARGE' || (error as HttpErrorResponse)?.status === 413) {
      return `The image is larger than ${this.maxUploadLabel}.`;
    }
    if (code === 'COVER_IMAGE_UNSUPPORTED') {
      return 'Choose a JPEG, PNG or WebP image.';
    }
    return 'The image could not be uploaded. Please try again.';
  }
}
