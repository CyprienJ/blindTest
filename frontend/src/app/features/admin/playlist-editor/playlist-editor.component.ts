import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  inject,
  OnInit,
  viewChild,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  CoverPickerComponent,
  CoverSelection,
  coverUpdate,
  KEEP_COVER,
} from '../../../shared/cover-picker/cover-picker.component';
import { AdminTrack } from '../track-list/admin-track.model';
import {
  AdminPlaylistDetail,
  AdminPlaylistSummary,
  PlaylistMetadata,
} from './admin-playlist.model';
import { AdminPlaylistService } from './admin-playlist.service';
import { PlaylistTrackPickerComponent } from './playlist-track-picker.component';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-playlist-editor',
  standalone: true,
  imports: [
    CommonModule,
    CoverPickerComponent,
    DragDropModule,
    ReactiveFormsModule,
    PlaylistTrackPickerComponent,
  ],
  templateUrl: './playlist-editor.component.html',
  styleUrl: './playlist-editor.component.scss',
})
export class PlaylistEditorComponent implements OnInit {
  private readonly playlistsService = inject(AdminPlaylistService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly picker = viewChild(PlaylistTrackPickerComponent);

  readonly metadataForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    description: ['', Validators.maxLength(1000)],
  });
  coverSelection: CoverSelection = KEEP_COVER;
  /** Changes whenever the cover picker must start fresh, discarding its uploaded tiles. */
  coverPickerKey = 0;
  playlists: AdminPlaylistSummary[] = [];
  selected: AdminPlaylistDetail | null = null;
  creating = false;
  loadingPlaylists = true;
  loadingDetail = false;
  savingMetadata = false;
  deleting = false;
  reordering = false;
  trackActionId: string | null = null;
  deleteConfirmation = false;
  listError = '';
  detailError = '';
  actionError = '';
  successMessage = '';

  ngOnInit(): void {
    this.loadPlaylists();
  }

  loadPlaylists(preferredId?: string): void {
    this.loadingPlaylists = true;
    this.listError = '';
    this.playlistsService
      .list()
      .pipe(finalize(() => (this.loadingPlaylists = false)))
      .subscribe({
        next: (playlists) => {
          this.playlists = playlists;
          if (this.creating) {
            return;
          }
          const id = preferredId ?? this.selected?.id ?? playlists[0]?.id;
          if (id && playlists.some((playlist) => playlist.id === id)) {
            this.loadPlaylist(id);
          } else {
            this.selected = null;
            this.resetMetadata(null);
          }
        },
        error: (error) => (this.listError = this.errorMessage(error)),
      });
  }

  refreshPlaylists(): void {
    if (this.confirmDiscard()) {
      this.loadPlaylists();
    }
  }

  selectPlaylist(id: string): void {
    if (this.selected?.id === id && !this.creating) {
      return;
    }
    if (!this.confirmDiscard()) {
      return;
    }
    this.creating = false;
    this.loadPlaylist(id);
  }

  startCreate(): void {
    if (!this.confirmDiscard()) {
      return;
    }
    this.creating = true;
    this.selected = null;
    this.detailError = '';
    this.actionError = '';
    this.deleteConfirmation = false;
    this.resetMetadata(null);
  }

  saveMetadata(): void {
    if (this.metadataForm.invalid || (!this.creating && !this.selected)) {
      this.metadataForm.markAllAsTouched();
      return;
    }
    const raw = this.metadataForm.getRawValue();
    const metadata: PlaylistMetadata = {
      name: raw.name.trim(),
      description: raw.description.trim() || null,
      ...coverUpdate(this.coverSelection),
    };
    this.savingMetadata = true;
    this.actionError = '';
    const request = this.creating
      ? this.playlistsService.create(metadata)
      : this.playlistsService.update(this.selected!.id, metadata);
    request.pipe(finalize(() => (this.savingMetadata = false))).subscribe({
      next: (playlist) => {
        const existingTracks = this.selected?.id === playlist.id ? this.selected.tracks : [];
        this.creating = false;
        this.resetMetadata(playlist);
        this.selected = { ...playlist, tracks: existingTracks };
        this.upsertSummary(playlist);
        this.showSuccess(
          existingTracks.length ? $localize`Playlist details saved.` : $localize`Playlist saved.`,
        );
      },
      error: (error) => (this.actionError = this.errorMessage(error)),
    });
  }

  requestDelete(): void {
    if (this.selected) {
      this.deleteConfirmation = true;
    }
  }

  confirmDelete(): void {
    if (!this.selected || this.deleting) {
      return;
    }
    const deletedId = this.selected.id;
    this.deleting = true;
    this.actionError = '';
    this.playlistsService
      .delete(deletedId)
      .pipe(finalize(() => (this.deleting = false)))
      .subscribe({
        next: () => {
          this.deleteConfirmation = false;
          this.selected = null;
          this.playlists = this.playlists.filter((playlist) => playlist.id !== deletedId);
          this.metadataForm.markAsPristine();
          this.showSuccess($localize`Playlist deleted.`);
          const next = this.playlists[0];
          if (next) {
            this.loadPlaylist(next.id);
          } else {
            this.startCreate();
          }
        },
        error: (error) => (this.actionError = this.errorMessage(error)),
      });
  }

  addTrack(track: AdminTrack): void {
    if (!this.selected || this.trackActionId || this.hasTrack(track.id)) {
      return;
    }
    this.trackActionId = track.id;
    this.actionError = '';
    this.playlistsService
      .addTrack(this.selected.id, track.id, this.selected.tracks.length)
      .pipe(finalize(() => (this.trackActionId = null)))
      .subscribe({
        next: (detail) => {
          this.applyMembershipDetail(detail);
          this.showSuccess($localize`${track.title} added to the playlist.`);
        },
        error: (error) => (this.actionError = this.errorMessage(error)),
      });
  }

  removeTrack(track: AdminTrack): void {
    if (!this.selected || this.trackActionId || this.reordering) {
      return;
    }
    const playlistId = this.selected.id;
    this.trackActionId = track.id;
    this.actionError = '';
    this.playlistsService
      .removeTrack(playlistId, track.id)
      .pipe(finalize(() => (this.trackActionId = null)))
      .subscribe({
        next: () => {
          if (!this.selected || this.selected.id !== playlistId) {
            return;
          }
          this.applyTracks(this.selected.tracks.filter((item) => item.id !== track.id));
          this.picker()?.reload();
          this.showSuccess($localize`${track.title} removed from the playlist.`);
        },
        error: (error) => (this.actionError = this.errorMessage(error)),
      });
  }

  dropTrack(event: CdkDragDrop<AdminTrack[]>): void {
    if (event.previousIndex !== event.currentIndex && !this.reordering) {
      this.reorderLocally(event.previousIndex, event.currentIndex);
    }
  }

  moveTrack(index: number, delta: number): void {
    const destination = index + delta;
    if (!this.selected || destination < 0 || destination >= this.selected.tracks.length) {
      return;
    }
    this.reorderLocally(index, destination);
  }

  hasTrack(trackId: string): boolean {
    return this.selected?.tracks.some((track) => track.id === trackId) ?? false;
  }

  get memberIds(): string[] {
    return this.selected?.tracks.map((track) => track.id) ?? [];
  }

  dragLabel(title: string): string {
    return $localize`Drag ${title}:title:`;
  }

  moveUpLabel(title: string): string {
    return $localize`Move ${title}:title: up`;
  }

  moveDownLabel(title: string): string {
    return $localize`Move ${title}:title: down`;
  }

  removeLabel(title: string): string {
    return $localize`Remove ${title}:title:`;
  }

  formatDuration(seconds: number | null): string {
    if (!seconds) {
      return '—';
    }
    const minutes = Math.floor(seconds / 60);
    return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
  }

  formatPosition(index: number): string {
    return String(index + 1).padStart(2, '0');
  }

  canDeactivate(): boolean {
    return !this.hasUnsavedChanges || window.confirm($localize`Discard unsaved playlist changes?`);
  }

  /** Whether the details form or the cover choice differ from the saved playlist. */
  get hasUnsavedChanges(): boolean {
    return this.metadataForm.dirty || this.coverSelection.kind !== 'keep';
  }

  @HostListener('window:beforeunload', ['$event'])
  warnBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.hasUnsavedChanges) {
      event.preventDefault();
    }
  }

  private loadPlaylist(id: string): void {
    this.loadingDetail = true;
    this.detailError = '';
    this.actionError = '';
    this.playlistsService
      .get(id)
      .pipe(finalize(() => (this.loadingDetail = false)))
      .subscribe({
        next: (playlist) => {
          this.selected = playlist;
          this.resetMetadata(playlist);
        },
        error: (error) => (this.detailError = this.errorMessage(error)),
      });
  }

  private resetMetadata(playlist: AdminPlaylistSummary | null): void {
    this.metadataForm.reset({
      name: playlist?.name ?? '',
      description: playlist?.description ?? '',
    });
    this.metadataForm.markAsPristine();
    this.coverSelection = KEEP_COVER;
    this.coverPickerKey++;
  }

  private reorderLocally(previousIndex: number, currentIndex: number): void {
    if (!this.selected || this.reordering) {
      return;
    }
    const playlistId = this.selected.id;
    const previous = [...this.selected.tracks];
    const reordered = [...previous];
    moveItemInArray(reordered, previousIndex, currentIndex);
    this.applyTracks(reordered);
    this.reordering = true;
    this.actionError = '';
    this.playlistsService
      .reorder(
        playlistId,
        reordered.map((track) => track.id),
      )
      .pipe(finalize(() => (this.reordering = false)))
      .subscribe({
        next: (detail) => {
          this.applyMembershipDetail(detail);
          this.showSuccess($localize`Track order saved.`);
        },
        error: (error) => {
          if (this.selected?.id === playlistId) {
            this.applyTracks(previous);
          }
          this.actionError = $localize`${this.errorMessage(error)} The previous order was restored.`;
        },
      });
  }

  private applyMembershipDetail(detail: AdminPlaylistDetail): void {
    if (!this.selected || this.selected.id !== detail.id) {
      return;
    }
    this.selected = { ...this.selected, trackCount: detail.trackCount, tracks: detail.tracks };
    this.upsertSummary(this.selected);
  }

  private applyTracks(tracks: AdminTrack[]): void {
    if (!this.selected) {
      return;
    }
    this.selected = { ...this.selected, tracks, trackCount: tracks.length };
    this.upsertSummary(this.selected);
  }

  private upsertSummary(playlist: AdminPlaylistSummary): void {
    const summary: AdminPlaylistSummary = {
      id: playlist.id,
      name: playlist.name,
      description: playlist.description,
      imageUrl: playlist.imageUrl,
      trackCount: playlist.trackCount,
    };
    const index = this.playlists.findIndex((item) => item.id === playlist.id);
    this.playlists =
      index < 0
        ? [...this.playlists, summary]
        : this.playlists.map((item) => (item.id === playlist.id ? summary : item));
  }

  private confirmDiscard(): boolean {
    return !this.hasUnsavedChanges || window.confirm($localize`Discard unsaved playlist changes?`);
  }

  private showSuccess(message: string): void {
    this.successMessage = message;
    window.setTimeout(() => {
      if (this.successMessage === message) {
        this.successMessage = '';
      }
    }, 4000);
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.message || $localize`The request could not be completed.`;
    }
    return $localize`The request could not be completed.`;
  }
}
