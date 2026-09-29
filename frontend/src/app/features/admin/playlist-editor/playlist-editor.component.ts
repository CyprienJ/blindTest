import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  HostListener,
  inject,
  OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs';
import { AdminTrack } from '../track-list/admin-track.model';
import { AdminTrackService } from '../track-list/admin-track.service';
import {
  AdminPlaylistDetail,
  AdminPlaylistSummary,
  PlaylistMetadata,
} from './admin-playlist.model';
import { AdminPlaylistService } from './admin-playlist.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-playlist-editor',
  standalone: true,
  imports: [CommonModule, DragDropModule, ReactiveFormsModule],
  templateUrl: './playlist-editor.component.html',
  styleUrl: './playlist-editor.component.scss',
})
export class PlaylistEditorComponent implements OnInit {
  private readonly playlistsService = inject(AdminPlaylistService);
  private readonly tracksService = inject(AdminTrackService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly metadataForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    description: ['', Validators.maxLength(1000)],
    imageUrl: ['', Validators.maxLength(500)],
  });
  readonly searchControl = this.formBuilder.nonNullable.control('');

  playlists: AdminPlaylistSummary[] = [];
  selected: AdminPlaylistDetail | null = null;
  availableTracks: AdminTrack[] = [];
  creating = false;
  loadingPlaylists = true;
  loadingDetail = false;
  loadingTracks = false;
  savingMetadata = false;
  deleting = false;
  reordering = false;
  trackActionId: string | null = null;
  deleteConfirmation = false;
  listError = '';
  detailError = '';
  catalogueError = '';
  actionError = '';
  successMessage = '';

  ngOnInit(): void {
    this.searchControl.valueChanges
      .pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadAvailableTracks());
    this.loadPlaylists();
    this.loadAvailableTracks();
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
            this.metadataForm.reset({ name: '', description: '', imageUrl: '' });
            this.metadataForm.markAsPristine();
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
    this.metadataForm.reset({ name: '', description: '', imageUrl: '' });
    this.metadataForm.markAsPristine();
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
      imageUrl: raw.imageUrl.trim() || null,
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
        this.metadataForm.reset({
          name: playlist.name,
          description: playlist.description ?? '',
          imageUrl: playlist.imageUrl ?? '',
        });
        this.metadataForm.markAsPristine();
        this.selected = { ...playlist, tracks: existingTracks };
        this.upsertSummary(playlist);
        this.showSuccess(existingTracks.length ? 'Playlist details saved.' : 'Playlist saved.');
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
          this.showSuccess('Playlist deleted.');
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
          this.showSuccess(`${track.title} added to the playlist.`);
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
          this.showSuccess(`${track.title} removed from the playlist.`);
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

  visibleAvailableTracks(): AdminTrack[] {
    return this.availableTracks.filter((track) => !this.hasTrack(track.id));
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
    return !this.metadataForm.dirty || window.confirm('Discard unsaved playlist changes?');
  }

  @HostListener('window:beforeunload', ['$event'])
  warnBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.metadataForm.dirty) {
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
          this.metadataForm.reset({
            name: playlist.name,
            description: playlist.description ?? '',
            imageUrl: playlist.imageUrl ?? '',
          });
          this.metadataForm.markAsPristine();
        },
        error: (error) => (this.detailError = this.errorMessage(error)),
      });
  }

  loadAvailableTracks(): void {
    this.loadingTracks = true;
    this.catalogueError = '';
    this.tracksService
      .list(0, 20, 'PUBLISHED', this.searchControl.value)
      .pipe(finalize(() => (this.loadingTracks = false)))
      .subscribe({
        next: (response) => (this.availableTracks = response.content),
        error: (error) => (this.catalogueError = this.errorMessage(error)),
      });
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
          this.showSuccess('Track order saved.');
        },
        error: (error) => {
          if (this.selected?.id === playlistId) {
            this.applyTracks(previous);
          }
          this.actionError = `${this.errorMessage(error)} The previous order was restored.`;
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
    return !this.metadataForm.dirty || window.confirm('Discard unsaved playlist changes?');
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
      return error.error?.message || error.message || 'The request could not be completed.';
    }
    return 'The request could not be completed.';
  }
}
