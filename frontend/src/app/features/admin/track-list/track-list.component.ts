import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../core/services/admin.service';
import { AdminTrack } from '../../../core/models/admin.model';
import { AdminSidebarComponent } from '../admin-sidebar.component';

@Component({
  selector: 'app-track-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, AdminSidebarComponent],
  template: `
    <div class="bt-screen" style="height: 100vh; overflow: hidden; flex-direction: column;">
      <div class="bt-shell">
        <app-admin-sidebar active="catalogue" />
        <div class="bt-main">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px;">
            <h1 class="bt-h1">Track catalogue</h1>
            <a routerLink="/admin/tracks/import" class="bt-btn bt-btn-primary">+ Import from YouTube</a>
          </div>
          <p class="bt-sub">All tracks across PENDING, PUBLISHED, REJECTED. Only PUBLISHED tracks are playable in sessions.</p>

          <div style="display: flex; gap: 10px; margin-bottom: 18px; align-items: center; flex-wrap: wrap;">
            <div class="bt-segment">
              <button [class.on]="statusFilter === 'all'" (click)="statusFilter = 'all'">All</button>
              <button [class.on]="statusFilter === 'PUBLISHED'" (click)="statusFilter = 'PUBLISHED'">Published</button>
              <button [class.on]="statusFilter === 'PENDING'" (click)="statusFilter = 'PENDING'">Pending</button>
              <button [class.on]="statusFilter === 'REJECTED'" (click)="statusFilter = 'REJECTED'">Rejected</button>
            </div>
            <div style="flex: 1;"></div>
            <input
              class="bt-input"
              style="width: 240px;"
              placeholder="Search tracks..."
              [(ngModel)]="searchQuery"
            />
          </div>

          @if (loading) {
            <div style="color: var(--fg-2); padding: 24px 0;">Loading…</div>
          } @else if (error) {
            <div class="bt-error" style="padding: 12px 0;">{{ error }}</div>
          } @else {
            <div class="bt-card" style="padding: 0;">
              <table class="bt-table">
                <thead>
                  <tr>
                    <th>Track</th>
                    <th>Category</th>
                    <th>Decade</th>
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  @for (track of filteredTracks; track track.id) {
                    <tr>
                      <td>
                        <div style="display: flex; align-items: center; gap: 12px;">
                          <div class="bt-placeholder" style="width: 36px; height: 36px; font-size: 10px;">♪</div>
                          <div>
                            <div style="font-size: 13.5px; font-weight: 600;">{{ track.title }}</div>
                            <div style="font-size: 11.5px; color: var(--fg-2);">{{ track.artist }}</div>
                          </div>
                        </div>
                      </td>
                      <td><span class="bt-chip">{{ track.category ?? '—' }}</span></td>
                      <td><span class="mono">{{ track.decade ? track.decade + 's' : '—' }}</span></td>
                      <td>
                        <span
                          class="bt-chip"
                          [class.bt-chip-accent]="track.status === 'PUBLISHED'"
                          [class.bt-chip-warn]="track.status === 'PENDING'"
                          [class.bt-chip-danger]="track.status === 'REJECTED'"
                        >
                          {{ track.status }}
                        </span>
                      </td>
                      <td style="text-align: right;">
                        <div style="display: flex; gap: 4px; justify-content: flex-end;">
                          @if (track.status === 'PENDING') {
                            <button
                              class="bt-btn bt-btn-primary bt-btn-sm"
                              (click)="validate(track)"
                              title="Publish"
                            >✓</button>
                            <button
                              class="bt-btn bt-btn-danger bt-btn-sm"
                              (click)="reject(track)"
                              title="Reject"
                            >✕</button>
                          }
                          <button
                            class="bt-btn bt-btn-ghost bt-btn-sm"
                            (click)="deleteTrack(track)"
                            title="Delete"
                          >⋯</button>
                        </div>
                      </td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="5" style="text-align: center; color: var(--fg-2); padding: 32px;">
                        No tracks found.
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      </div>
    </div>
  `,
})
export class TrackListComponent implements OnInit {
  private readonly adminService = inject(AdminService);

  tracks: AdminTrack[] = [];
  loading = true;
  error = '';
  statusFilter = 'all';
  searchQuery = '';

  get filteredTracks(): AdminTrack[] {
    const q = this.searchQuery.toLowerCase();
    return this.tracks.filter((t) => {
      const matchesStatus = this.statusFilter === 'all' || t.status === this.statusFilter;
      const matchesSearch =
        !q || t.title.toLowerCase().includes(q) || t.artist.toLowerCase().includes(q);
      return matchesStatus && matchesSearch;
    });
  }

  ngOnInit(): void {
    this.loadTracks();
  }

  loadTracks(): void {
    this.loading = true;
    this.error = '';
    this.adminService.getTracks().subscribe({
      next: (page) => {
        this.tracks = page.content;
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load tracks.';
        this.loading = false;
      },
    });
  }

  validate(track: AdminTrack): void {
    this.adminService.validateTrack(track.id).subscribe({
      next: (updated) => {
        this.tracks = this.tracks.map((t) => (t.id === updated.id ? updated : t));
      },
    });
  }

  reject(track: AdminTrack): void {
    this.adminService.rejectTrack(track.id).subscribe({
      next: (updated) => {
        this.tracks = this.tracks.map((t) => (t.id === updated.id ? updated : t));
      },
    });
  }

  deleteTrack(track: AdminTrack): void {
    if (!confirm(`Delete "${track.title}"?`)) return;
    this.adminService.deleteTrack(track.id).subscribe({
      next: () => {
        this.tracks = this.tracks.filter((t) => t.id !== track.id);
      },
    });
  }
}
