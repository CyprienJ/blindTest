import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { AdminService } from '../../../core/services/admin.service';
import { AdminPlaylist, AdminPlaylistDetail, AdminTrack } from '../../../core/models/admin.model';
import { AdminSidebarComponent } from '../admin-sidebar.component';

@Component({
  selector: 'app-playlist-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, DragDropModule, AdminSidebarComponent],
  template: `
    <div class="bt-screen" style="height: 100vh; overflow: hidden; flex-direction: column;">
      <div class="bt-shell">
        <app-admin-sidebar active="playlists" />
        <div class="bt-main">

          @if (!selected) {
            <!-- Playlist list view -->
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px;">
              <h1 class="bt-h1">Playlists</h1>
              <button class="bt-btn bt-btn-primary" (click)="showCreateForm = true">+ New playlist</button>
            </div>
            <p class="bt-sub">Create and manage themed playlists for blindtest sessions.</p>

            @if (showCreateForm) {
              <div class="bt-card" style="margin-bottom: 16px;">
                <h2 class="bt-h2" style="margin-bottom: 12px;">New playlist</h2>
                <div class="bt-field" style="margin-bottom: 10px;">
                  <label class="bt-label">Name</label>
                  <input class="bt-input" [(ngModel)]="newPlaylistName" placeholder="Playlist name" />
                </div>
                <div class="bt-field" style="margin-bottom: 14px;">
                  <label class="bt-label">Description</label>
                  <input class="bt-input" [(ngModel)]="newPlaylistDescription" placeholder="Optional description" />
                </div>
                <div style="display: flex; gap: 8px;">
                  <button class="bt-btn bt-btn-primary" (click)="createPlaylist()" [disabled]="!newPlaylistName.trim()">Create</button>
                  <button class="bt-btn bt-btn-ghost" (click)="showCreateForm = false; newPlaylistName = ''; newPlaylistDescription = '';">Cancel</button>
                </div>
              </div>
            }

            @if (loadingPlaylists) {
              <div style="color: var(--fg-2); padding: 24px 0;">Loading…</div>
            } @else {
              <div style="display: flex; flex-direction: column; gap: 8px;">
                @for (pl of playlists; track pl.id) {
                  <div
                    class="bt-card"
                    style="padding: 14px 18px; display: flex; align-items: center; gap: 14px; cursor: pointer;"
                    (click)="openPlaylist(pl)"
                  >
                    <div class="bt-placeholder" style="width: 48px; height: 48px; flex-shrink: 0;">♫</div>
                    <div style="flex: 1;">
                      <div style="font-size: 14px; font-weight: 700;">{{ pl.name }}</div>
                      @if (pl.description) {
                        <div style="font-size: 12px; color: var(--fg-2); margin-top: 2px;">{{ pl.description }}</div>
                      }
                    </div>
                    <span class="bt-chip">{{ pl.trackCount }} tracks</span>
                    <button class="bt-btn bt-btn-ghost bt-btn-sm" (click)="$event.stopPropagation(); deletePlaylist(pl)">✕</button>
                  </div>
                } @empty {
                  <div style="color: var(--fg-3); padding: 32px; text-align: center;">No playlists yet. Create your first one.</div>
                }
              </div>
            }
          }

          @if (selected) {
            <!-- Playlist detail view -->
            <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 4px;">
              <button class="bt-btn bt-btn-ghost bt-btn-sm" (click)="closePlaylist()">← Back</button>
              <h1 class="bt-h1" style="margin: 0;">{{ selected.name }}</h1>
              <span class="bt-chip">{{ selected.tracks.length }} tracks</span>
            </div>
            <p class="bt-sub">{{ selected.description ?? 'No description' }}</p>

            <div style="display: grid; grid-template-columns: 1.4fr 1fr; gap: 18px;">
              <!-- Left: draggable track list -->
              <div class="bt-card">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                  <h2 class="bt-h2">Tracks · drag to reorder</h2>
                  @if (orderChanged) {
                    <button class="bt-btn bt-btn-primary bt-btn-sm" (click)="saveOrder()">Save order</button>
                  }
                </div>
                <div cdkDropList (cdkDropListDropped)="drop($event)" style="display: flex; flex-direction: column; gap: 4px;">
                  @for (track of selected.tracks; track track.id; let i = $index) {
                    <div
                      cdkDrag
                      style="display: flex; align-items: center; gap: 12px; padding: 10px; background: var(--bg-2); border-radius: var(--r-md); cursor: grab;"
                    >
                      <div style="color: var(--fg-3); font-family: var(--font-mono); font-size: 11px; width: 24px;">{{ (i + 1).toString().padStart(2, '0') }}</div>
                      <div style="color: var(--fg-3);">⠿</div>
                      <div class="bt-placeholder" style="width: 32px; height: 32px; font-size: 10px;">♪</div>
                      <div style="flex: 1;">
                        <div style="font-size: 13px; font-weight: 600;">{{ track.title }}</div>
                        <div style="font-size: 11.5px; color: var(--fg-2);">{{ track.artist }}</div>
                      </div>
                      <button class="bt-btn bt-btn-ghost bt-btn-sm" (click)="removeTrack(track)">✕</button>
                    </div>
                  } @empty {
                    <div style="color: var(--fg-3); text-align: center; padding: 24px;">No tracks yet. Add some from the right panel.</div>
                  }
                </div>
              </div>

              <!-- Right: details + add tracks -->
              <div style="display: flex; flex-direction: column; gap: 14px;">
                <div class="bt-card">
                  <h2 class="bt-h2" style="margin-bottom: 12px;">Details</h2>
                  <div class="bt-field" style="margin-bottom: 10px;">
                    <label class="bt-label">Name</label>
                    <input class="bt-input" [(ngModel)]="editName" />
                  </div>
                  <div class="bt-field" style="margin-bottom: 14px;">
                    <label class="bt-label">Description</label>
                    <textarea class="bt-input" rows="3" style="height: auto; padding: 10px 14px; resize: none;" [(ngModel)]="editDescription"></textarea>
                  </div>
                  <button class="bt-btn bt-btn-primary" style="width: 100%;" (click)="saveDetails()">Save details</button>
                </div>

                <div class="bt-card">
                  <h2 class="bt-h2" style="margin-bottom: 10px;">Add tracks</h2>
                  <div style="position: relative; margin-bottom: 10px;">
                    <input class="bt-input" placeholder="Search catalogue…" [(ngModel)]="trackSearch" style="padding-left: 34px;" />
                    <div style="position: absolute; left: 10px; top: 13px; color: var(--fg-3);">🔍</div>
                  </div>
                  @if (loadingCatalogue) {
                    <div style="color: var(--fg-2); font-size: 13px; padding: 8px 0;">Loading…</div>
                  } @else {
                    <div style="display: flex; flex-direction: column; gap: 4px; max-height: 280px; overflow-y: auto;">
                      @for (track of filteredCatalogue; track track.id) {
                        <div style="display: flex; align-items: center; gap: 10px; padding: 8px; border-radius: var(--r-sm);">
                          <div class="bt-placeholder" style="width: 28px; height: 28px; font-size: 10px;">♪</div>
                          <div style="flex: 1;">
                            <div style="font-size: 12.5px; font-weight: 600;">{{ track.title }}</div>
                            <div style="font-size: 11px; color: var(--fg-3);">{{ track.artist }}</div>
                          </div>
                          <button class="bt-btn bt-btn-ghost bt-btn-sm" (click)="addTrack(track)" [disabled]="isInPlaylist(track.id)">
                            {{ isInPlaylist(track.id) ? '✓' : '+' }}
                          </button>
                        </div>
                      } @empty {
                        <div style="color: var(--fg-3); font-size: 13px; padding: 8px;">No results.</div>
                      }
                    </div>
                  }
                </div>
              </div>
            </div>
          }

        </div>
      </div>
    </div>
  `,
})
export class PlaylistEditorComponent implements OnInit {
  private readonly adminService = inject(AdminService);

  playlists: AdminPlaylist[] = [];
  selected: AdminPlaylistDetail | null = null;
  loadingPlaylists = true;
  loadingCatalogue = false;

  showCreateForm = false;
  newPlaylistName = '';
  newPlaylistDescription = '';

  editName = '';
  editDescription = '';
  orderChanged = false;

  catalogue: AdminTrack[] = [];
  trackSearch = '';

  get filteredCatalogue(): AdminTrack[] {
    const q = this.trackSearch.toLowerCase();
    return this.catalogue.filter(
      (t) => t.status === 'PUBLISHED' && (!q || t.title.toLowerCase().includes(q) || t.artist.toLowerCase().includes(q)),
    );
  }

  isInPlaylist(trackId: string): boolean {
    return this.selected?.tracks.some((t) => t.id === trackId) ?? false;
  }

  ngOnInit(): void {
    this.loadPlaylists();
    this.loadCatalogue();
  }

  loadPlaylists(): void {
    this.loadingPlaylists = true;
    this.adminService.getPlaylists().subscribe({
      next: (list) => { this.playlists = list; this.loadingPlaylists = false; },
      error: () => { this.loadingPlaylists = false; },
    });
  }

  loadCatalogue(): void {
    this.loadingCatalogue = true;
    this.adminService.getTracks(0, 200).subscribe({
      next: (page) => { this.catalogue = page.content; this.loadingCatalogue = false; },
      error: () => { this.loadingCatalogue = false; },
    });
  }

  openPlaylist(pl: AdminPlaylist): void {
    this.adminService.getPlaylist(pl.id).subscribe({
      next: (detail) => {
        this.selected = detail;
        this.editName = detail.name;
        this.editDescription = detail.description ?? '';
        this.orderChanged = false;
      },
    });
  }

  closePlaylist(): void {
    this.selected = null;
    this.orderChanged = false;
    this.loadPlaylists();
  }

  createPlaylist(): void {
    const name = this.newPlaylistName.trim();
    if (!name) return;
    this.adminService.createPlaylist(name, this.newPlaylistDescription.trim() || undefined).subscribe({
      next: () => {
        this.showCreateForm = false;
        this.newPlaylistName = '';
        this.newPlaylistDescription = '';
        this.loadPlaylists();
      },
    });
  }

  deletePlaylist(pl: AdminPlaylist): void {
    if (!confirm(`Delete playlist "${pl.name}"?`)) return;
    this.adminService.deletePlaylist(pl.id).subscribe({
      next: () => { this.playlists = this.playlists.filter((p) => p.id !== pl.id); },
    });
  }

  saveDetails(): void {
    if (!this.selected) return;
    this.adminService.updatePlaylist(this.selected.id, { name: this.editName, description: this.editDescription }).subscribe({
      next: (updated) => {
        this.selected = { ...this.selected!, name: updated.name, description: updated.description };
      },
    });
  }

  drop(event: CdkDragDrop<AdminTrack[]>): void {
    if (!this.selected) return;
    moveItemInArray(this.selected.tracks, event.previousIndex, event.currentIndex);
    this.orderChanged = true;
  }

  saveOrder(): void {
    if (!this.selected) return;
    const requests = this.selected.tracks.map((track, i) =>
      this.adminService.addTrackToPlaylist(this.selected!.id, track.id, i + 1),
    );
    let completed = 0;
    requests.forEach((req) => {
      req.subscribe({
        next: (detail) => {
          completed++;
          if (completed === requests.length) {
            this.selected = detail;
            this.orderChanged = false;
          }
        },
      });
    });
  }

  addTrack(track: AdminTrack): void {
    if (!this.selected || this.isInPlaylist(track.id)) return;
    const order = this.selected.tracks.length + 1;
    this.adminService.addTrackToPlaylist(this.selected.id, track.id, order).subscribe({
      next: (detail) => { this.selected = detail; },
    });
  }

  removeTrack(track: AdminTrack): void {
    if (!this.selected) return;
    this.adminService.removeTrackFromPlaylist(this.selected.id, track.id).subscribe({
      next: () => {
        this.selected = { ...this.selected!, tracks: this.selected!.tracks.filter((t) => t.id !== track.id) };
        this.orderChanged = false;
      },
    });
  }
}
