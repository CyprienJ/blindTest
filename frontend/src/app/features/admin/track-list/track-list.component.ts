import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, finalize, forkJoin } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AdminTrack,
  hasStreamingLinks,
  MediaPreview,
  SortDirection,
  TrackAlias,
  TrackAnswerType,
  TrackSortField,
  TrackStatus,
  TrackUpdate,
} from './admin-track.model';
import { AdminTrackService } from './admin-track.service';
import {
  STREAMING_LINK_PLATFORMS,
  streamingLinkControls,
  streamingLinkFormValue,
  streamingLinksUpdate,
} from './streaming-link-fields';
import { DifficultyRatingComponent } from '../../../shared/difficulty-rating/difficulty-rating.component';
import {
  CoverPickerComponent,
  CoverSelection,
  coverUpdate,
  KEEP_COVER,
} from '../../../shared/cover-picker/cover-picker.component';
import { ExcerptEditorComponent } from '../../../shared/excerpt-editor/excerpt-editor.component';

type StatusFilter = TrackStatus | null;
type ConfirmationAction = 'reject' | 'delete';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-track-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    DifficultyRatingComponent,
    ExcerptEditorComponent,
    CoverPickerComponent,
  ],
  templateUrl: './track-list.component.html',
  styleUrl: './track-list.component.scss',
})
export class TrackListComponent implements OnInit {
  private readonly service = inject(AdminTrackService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly pageSize = 10;
  readonly filters: { value: StatusFilter; label: string }[] = [
    { value: null, label: 'All tracks' },
    { value: 'PUBLISHED', label: 'Published' },
    { value: 'PENDING', label: 'Pending review' },
    { value: 'REJECTED', label: 'Rejected' },
  ];
  readonly aliasTypes: TrackAnswerType[] = ['TITLE', 'ARTIST', 'COMPOSER', 'ALBUM'];

  readonly searchControl = this.formBuilder.nonNullable.control('');
  readonly editForm = this.formBuilder.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    artist: ['', [Validators.required, Validators.maxLength(255)]],
    album: ['', Validators.maxLength(255)],
    composer: ['', Validators.maxLength(255)],
    work: ['', Validators.maxLength(255)],
    category: ['', [Validators.required, Validators.maxLength(100)]],
    decade: [2000, [Validators.required, Validators.min(1900), Validators.max(2100)]],
    durationSeconds: [1, [Validators.required, Validators.min(1)]],
    excerptStartSeconds: [0, [Validators.required, Validators.min(0)]],
    excerptDurationSeconds: [60, [Validators.required, Validators.min(5), Validators.max(60)]],
    imageUrl: [''],
    difficulty: this.formBuilder.control<number | null>(null),
    streamingLinks: this.formBuilder.nonNullable.group(streamingLinkControls()),
  });
  readonly streamingPlatforms = STREAMING_LINK_PLATFORMS;
  readonly aliasForm = this.formBuilder.nonNullable.group({
    type: ['TITLE' as TrackAnswerType, Validators.required],
    value: ['', [Validators.required, Validators.maxLength(255)]],
  });

  tracks: AdminTrack[] = [];
  counts: Record<string, number> = { ALL: 0, PUBLISHED: 0, PENDING: 0, REJECTED: 0 };
  activeStatus: StatusFilter = null;
  sortField: TrackSortField = 'ADDED';
  sortDirection: SortDirection = 'DESC';
  readonly hasStreamingLinks = hasStreamingLinks;
  page = 0;
  totalPages = 0;
  totalElements = 0;
  loading = true;
  listError = '';
  successMessage = '';

  editingTrack: AdminTrack | null = null;
  coverSelection: CoverSelection = KEEP_COVER;
  aliases: TrackAlias[] = [];
  aliasesLoading = false;
  editSaving = false;
  editError = '';
  aliasSaving = false;
  aliasError = '';
  editingAliasId: string | null = null;

  editPreview: MediaPreview | null = null;
  editPeaks: number[] = [];
  editMediaLoading = false;
  editMediaError = '';

  previewTrack: AdminTrack | null = null;
  preview: MediaPreview | null = null;
  previewLoading = false;
  previewError = '';

  confirmation: { track: AdminTrack; action: ConfirmationAction } | null = null;
  actionTrackId: string | null = null;

  ngOnInit(): void {
    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.page = 0;
        this.loadTracks();
      });
    this.refresh();
  }

  refresh(): void {
    this.loadTracks();
    this.loadCounts();
  }

  selectStatus(status: StatusFilter): void {
    if (this.activeStatus === status) {
      return;
    }
    this.activeStatus = status;
    this.page = 0;
    this.loadTracks();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages || page === this.page) {
      return;
    }
    this.page = page;
    this.loadTracks();
  }

  openEdit(track: AdminTrack): void {
    this.editingTrack = track;
    this.coverSelection = KEEP_COVER;
    this.editError = '';
    this.aliasError = '';
    this.editingAliasId = null;
    this.aliasForm.reset({ type: 'TITLE', value: '' });
    this.editForm.reset({
      title: track.title,
      artist: track.artist,
      album: track.album ?? '',
      composer: track.composer ?? '',
      work: track.work ?? '',
      category: track.category ?? '',
      decade: track.decade ?? 2000,
      durationSeconds: track.durationSeconds ?? 1,
      excerptStartSeconds: track.excerptStartSeconds,
      excerptDurationSeconds: track.excerptDurationSeconds ?? 60,
      imageUrl: track.imageUrl ?? '',
      difficulty: track.difficulty,
      streamingLinks: streamingLinkFormValue(track),
    });
    this.loadEditMedia(track);
    this.aliases = [];
    this.aliasesLoading = true;
    this.service
      .listAliases(track.id)
      .pipe(finalize(() => (this.aliasesLoading = false)))
      .subscribe({
        next: (aliases) => (this.aliases = aliases),
        error: (error) => (this.aliasError = this.errorMessage(error)),
      });
  }

  closeEdit(): void {
    if (!this.editSaving && !this.aliasSaving) {
      this.editingTrack = null;
      this.editPreview = null;
      this.editPeaks = [];
    }
  }

  saveTrack(): void {
    if (!this.editingTrack || this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const { difficulty, streamingLinks, ...value } = this.editForm.getRawValue();
    const update: TrackUpdate = {
      ...value,
      streamingLinks: streamingLinksUpdate(streamingLinks),
      difficulty: difficulty ?? undefined,
      album: value.album.trim() || null,
      composer: value.composer.trim() || null,
      work: value.work.trim() || null,
      imageUrl: value.imageUrl.trim() || null,
      ...coverUpdate(this.coverSelection),
    };
    this.editSaving = true;
    this.editError = '';
    this.service
      .update(this.editingTrack.id, update)
      .pipe(finalize(() => (this.editSaving = false)))
      .subscribe({
        next: (track) => {
          this.replaceTrack(track);
          this.editingTrack = track;
          this.coverSelection = KEEP_COVER;
          this.showSuccess('Track metadata saved.');
        },
        error: (error) => (this.editError = this.errorMessage(error)),
      });
  }

  setExcerptStart(start: number): void {
    this.editForm.controls.excerptStartSeconds.setValue(start);
    this.editForm.markAsDirty();
  }

  setExcerptWindow(window: number): void {
    this.editForm.controls.excerptDurationSeconds.setValue(window);
    this.editForm.markAsDirty();
  }

  /** Loads the private-media preview and waveform used by the editor's excerpt picker. */
  loadEditMedia(track: AdminTrack): void {
    this.editPreview = null;
    this.editPeaks = [];
    this.editMediaError = '';
    if (!track.audioChecksum) {
      return;
    }
    this.editMediaLoading = true;
    this.service
      .preview(track.id)
      .pipe(finalize(() => (this.editMediaLoading = false)))
      .subscribe({
        next: (preview) => {
          if (this.editingTrack?.id === track.id) {
            this.editPreview = preview;
          }
        },
        error: (error) => (this.editMediaError = this.errorMessage(error)),
      });
    this.service.waveform(track.id).subscribe({
      next: (waveform) => {
        if (this.editingTrack?.id === track.id) {
          this.editPeaks = waveform.peaks;
        }
      },
      // Older imports have no waveform; the sliders still work without it.
      error: () => (this.editPeaks = []),
    });
  }

  editAlias(alias: TrackAlias): void {
    this.editingAliasId = alias.id;
    this.aliasForm.setValue({ type: alias.type, value: alias.value });
  }

  cancelAliasEdit(): void {
    this.editingAliasId = null;
    this.aliasForm.reset({ type: 'TITLE', value: '' });
  }

  saveAlias(): void {
    if (!this.editingTrack || this.aliasForm.invalid) {
      this.aliasForm.markAllAsTouched();
      return;
    }
    const { type, value } = this.aliasForm.getRawValue();
    this.aliasSaving = true;
    this.aliasError = '';
    this.service
      .saveAlias(this.editingTrack.id, type, value.trim(), this.editingAliasId ?? undefined)
      .pipe(finalize(() => (this.aliasSaving = false)))
      .subscribe({
        next: (alias) => {
          const index = this.aliases.findIndex((item) => item.id === alias.id);
          this.aliases =
            index < 0
              ? [...this.aliases, alias]
              : this.aliases.map((item) => (item.id === alias.id ? alias : item));
          this.cancelAliasEdit();
          this.showSuccess(index < 0 ? 'Alternative answer added.' : 'Alternative answer saved.');
        },
        error: (error) => (this.aliasError = this.errorMessage(error)),
      });
  }

  deleteAlias(alias: TrackAlias): void {
    if (!this.editingTrack || this.aliasSaving) {
      return;
    }
    this.aliasSaving = true;
    this.aliasError = '';
    this.service
      .deleteAlias(this.editingTrack.id, alias.id)
      .pipe(finalize(() => (this.aliasSaving = false)))
      .subscribe({
        next: () => {
          this.aliases = this.aliases.filter((item) => item.id !== alias.id);
          this.showSuccess('Alternative answer deleted.');
        },
        error: (error) => (this.aliasError = this.errorMessage(error)),
      });
  }

  openPreview(track: AdminTrack): void {
    this.previewTrack = track;
    this.preview = null;
    this.previewError = '';
    this.previewLoading = true;
    this.service
      .preview(track.id)
      .pipe(finalize(() => (this.previewLoading = false)))
      .subscribe({
        next: (preview) => (this.preview = preview),
        error: (error) => (this.previewError = this.errorMessage(error)),
      });
  }

  closePreview(): void {
    this.previewTrack = null;
    this.preview = null;
  }

  validate(track: AdminTrack): void {
    this.runTrackAction(track, () => this.service.validate(track.id));
  }

  requestConfirmation(track: AdminTrack, action: ConfirmationAction): void {
    this.confirmation = { track, action };
  }

  closeConfirmation(): void {
    if (!this.actionTrackId) {
      this.confirmation = null;
    }
  }

  confirmAction(): void {
    if (!this.confirmation) {
      return;
    }
    const { track, action } = this.confirmation;
    if (action === 'delete') {
      this.actionTrackId = track.id;
      this.listError = '';
      this.service
        .delete(track.id)
        .pipe(finalize(() => (this.actionTrackId = null)))
        .subscribe({
          next: () => {
            this.confirmation = null;
            this.showSuccess('Track deleted.');
            this.refreshAfterRemoval();
          },
          error: (error) => {
            this.confirmation = null;
            this.listError = this.errorMessage(error);
          },
        });
      return;
    }
    this.runTrackAction(
      track,
      () => this.service.reject(track.id),
      () => {
        this.confirmation = null;
      },
    );
  }

  formatDuration(seconds: number | null): string {
    if (seconds === null) {
      return '—';
    }
    const minutes = Math.floor(seconds / 60);
    return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
  }

  statusLabel(status: TrackStatus): string {
    return status === 'PENDING'
      ? 'Pending review'
      : status.charAt(0) + status.slice(1).toLowerCase();
  }

  countFor(status: StatusFilter): number {
    return this.counts[status ?? 'ALL'];
  }

  /** Toggles the direction of the current column, or starts a new column in its natural order. */
  sortBy(field: TrackSortField): void {
    if (this.sortField === field) {
      this.sortDirection = this.sortDirection === 'ASC' ? 'DESC' : 'ASC';
    } else {
      this.sortField = field;
      this.sortDirection = field === 'ADDED' ? 'DESC' : 'ASC';
    }
    this.page = 0;
    this.loadTracks();
  }

  sortIndicator(field: TrackSortField): string {
    if (this.sortField !== field) {
      return '↕';
    }
    return this.sortDirection === 'ASC' ? '↑' : '↓';
  }

  ariaSort(field: TrackSortField): 'ascending' | 'descending' | 'none' {
    if (this.sortField !== field) {
      return 'none';
    }
    return this.sortDirection === 'ASC' ? 'ascending' : 'descending';
  }

  trackById(_index: number, track: AdminTrack): string {
    return track.id;
  }

  private loadTracks(): void {
    this.loading = true;
    this.listError = '';
    this.service
      .list(this.page, this.pageSize, this.activeStatus, this.searchControl.value, {
        sort: this.sortField,
        direction: this.sortDirection,
      })
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (response) => {
          this.tracks = response.content;
          this.totalPages = response.totalPages;
          this.totalElements = response.totalElements;
          this.page = response.number;
        },
        error: (error) => {
          this.tracks = [];
          this.listError = this.errorMessage(error);
        },
      });
  }

  private loadCounts(): void {
    forkJoin({
      ALL: this.service.list(0, 1),
      PUBLISHED: this.service.list(0, 1, 'PUBLISHED'),
      PENDING: this.service.list(0, 1, 'PENDING'),
      REJECTED: this.service.list(0, 1, 'REJECTED'),
    }).subscribe({
      next: (responses) => {
        this.counts = {
          ALL: responses.ALL.totalElements,
          PUBLISHED: responses.PUBLISHED.totalElements,
          PENDING: responses.PENDING.totalElements,
          REJECTED: responses.REJECTED.totalElements,
        };
      },
    });
  }

  private runTrackAction(
    track: AdminTrack,
    action: () => ReturnType<AdminTrackService['validate']>,
    beforeSuccess?: () => void,
  ): void {
    this.actionTrackId = track.id;
    this.listError = '';
    action()
      .pipe(finalize(() => (this.actionTrackId = null)))
      .subscribe({
        next: (updated) => {
          beforeSuccess?.();
          this.showSuccess(updated.status === 'PUBLISHED' ? 'Track published.' : 'Track rejected.');
          if (this.activeStatus && updated.status !== this.activeStatus) {
            this.refreshAfterRemoval();
          } else {
            this.replaceTrack(updated);
            this.loadCounts();
          }
        },
        error: (error) => {
          this.confirmation = null;
          this.listError = this.errorMessage(error);
        },
      });
  }

  private replaceTrack(updated: AdminTrack): void {
    this.tracks = this.tracks.map((track) => (track.id === updated.id ? updated : track));
  }

  private refreshAfterRemoval(): void {
    if (this.tracks.length === 1 && this.page > 0) {
      this.page--;
    }
    this.refresh();
  }

  private showSuccess(message: string): void {
    this.successMessage = message;
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const code = error.error?.code as string | undefined;
      const message = error.error?.message as string | undefined;
      if (code === 'TRACK_METADATA_INCOMPLETE') {
        return 'Complete all required metadata before publishing this track.';
      }
      if (code === 'TRACK_EXCERPT_INVALID') {
        return 'The excerpt window does not fit in the track: move its start or shorten it.';
      }
      if (code === 'IMPORT_NOT_DONE' || code === 'INVALID_IMPORT_STATUS_TRANSITION') {
        return 'This import is not ready for review.';
      }
      if (error.status === 0) {
        return 'The server cannot be reached. Check your connection and try again.';
      }
      return message || 'The operation could not be completed. Please try again.';
    }
    return 'The operation could not be completed. Please try again.';
  }
}
