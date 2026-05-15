import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../core/services/admin.service';
import { AdminUser } from '../../../core/models/admin.model';
import { AdminSidebarComponent } from '../admin-sidebar.component';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule, AdminSidebarComponent],
  template: `
    <div class="bt-screen" style="height: 100vh; overflow: hidden; flex-direction: column;">
      <div class="bt-shell">
        <app-admin-sidebar active="users" />
        <div class="bt-main">
          <h1 class="bt-h1">User management</h1>
          <p class="bt-sub">Promote moderators, suspend abusive accounts, delete users.</p>

          <!-- Stats cards -->
          <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 18px;">
            <div class="bt-card" style="padding: 14px;">
              <div style="font-size: 11px; color: var(--fg-2); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600;">Total users</div>
              <div class="mono" style="font-size: 22px; font-weight: 700; margin-top: 4px;">{{ users.length }}</div>
            </div>
            <div class="bt-card" style="padding: 14px;">
              <div style="font-size: 11px; color: var(--fg-2); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600;">Verified</div>
              <div class="mono" style="font-size: 22px; font-weight: 700; margin-top: 4px;">{{ verifiedCount }}</div>
            </div>
            <div class="bt-card" style="padding: 14px;">
              <div style="font-size: 11px; color: var(--fg-2); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600;">Admins</div>
              <div class="mono" style="font-size: 22px; font-weight: 700; margin-top: 4px;">{{ adminCount }}</div>
            </div>
            <div class="bt-card" style="padding: 14px;">
              <div style="font-size: 11px; color: var(--fg-2); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600;">Unverified</div>
              <div class="mono" style="font-size: 22px; font-weight: 700; margin-top: 4px;">{{ unverifiedCount }}</div>
            </div>
          </div>

          <!-- Filters -->
          <div style="display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap;">
            <div style="position: relative; flex: 1; max-width: 360px;">
              <input class="bt-input" placeholder="Search by username or email…" [(ngModel)]="searchQuery" style="padding-left: 34px;" />
              <div style="position: absolute; left: 10px; top: 13px; color: var(--fg-3);">🔍</div>
            </div>
            <div class="bt-segment">
              <button [class.on]="roleFilter === 'all'" (click)="roleFilter = 'all'">All</button>
              <button [class.on]="roleFilter === 'USER'" (click)="roleFilter = 'USER'">Users</button>
              <button [class.on]="roleFilter === 'ADMIN'" (click)="roleFilter = 'ADMIN'">Admins</button>
              <button [class.on]="roleFilter === 'unverified'" (click)="roleFilter = 'unverified'">Unverified</button>
            </div>
          </div>

          @if (loading) {
            <div style="color: var(--fg-2); padding: 24px 0;">Loading…</div>
          } @else if (error) {
            <div style="background: var(--bg-2); border: 1px solid var(--line-soft); border-radius: var(--r-lg); padding: 32px; text-align: center; color: var(--fg-2);">
              <div style="font-size: 14px; font-weight: 600; margin-bottom: 6px;">User management not yet available</div>
              <div style="font-size: 12.5px;">The admin users API is not yet implemented.</div>
            </div>
          } @else {
            <div class="bt-card" style="padding: 0;">
              <table class="bt-table">
                <thead>
                  <tr>
                    <th>User</th>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Joined</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  @for (user of filteredUsers; track user.id) {
                    <tr>
                      <td>
                        <div style="display: flex; align-items: center; gap: 12px;">
                          <div class="bt-avatar" style="width: 32px; height: 32px; font-size: 12px;">
                            {{ user.username.charAt(0).toUpperCase() }}
                          </div>
                          <div>
                            <div style="font-size: 13.5px; font-weight: 600;">{{ user.username }}</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <div style="display: flex; align-items: center; gap: 8px;">
                          <span>{{ user.email }}</span>
                          @if (user.emailVerified) {
                            <span class="bt-chip bt-chip-accent" style="height: 18px; font-size: 10px;">✓</span>
                          } @else {
                            <span class="bt-chip bt-chip-warn" style="height: 18px; font-size: 10px;">unverified</span>
                          }
                        </div>
                      </td>
                      <td>
                        <span class="bt-chip"
                          [style.background]="user.role === 'ADMIN' ? 'oklch(0.32 0.10 80)' : 'var(--bg-2)'"
                          [style.color]="user.role === 'ADMIN' ? 'var(--warn)' : 'var(--fg-1)'"
                        >{{ user.role }}</span>
                      </td>
                      <td style="color: var(--fg-2); font-size: 12.5px;">{{ user.createdAt | date:'MMM yyyy' }}</td>
                      <td style="text-align: right;">
                        <button class="bt-btn bt-btn-ghost bt-btn-sm">⋯</button>
                      </td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="5" style="text-align: center; color: var(--fg-2); padding: 32px;">No users found.</td>
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
export class UserManagementComponent implements OnInit {
  private readonly adminService = inject(AdminService);

  users: AdminUser[] = [];
  loading = true;
  error = false;
  searchQuery = '';
  roleFilter = 'all';

  get filteredUsers(): AdminUser[] {
    const q = this.searchQuery.toLowerCase();
    return this.users.filter((u) => {
      const matchesRole =
        this.roleFilter === 'all' ||
        (this.roleFilter === 'unverified' && !u.emailVerified) ||
        u.role === this.roleFilter;
      const matchesSearch =
        !q || u.username.toLowerCase().includes(q) || u.email.toLowerCase().includes(q);
      return matchesRole && matchesSearch;
    });
  }

  get verifiedCount(): number { return this.users.filter((u) => u.emailVerified).length; }
  get adminCount(): number { return this.users.filter((u) => u.role === 'ADMIN').length; }
  get unverifiedCount(): number { return this.users.filter((u) => !u.emailVerified).length; }

  ngOnInit(): void {
    this.adminService.getUsers().subscribe({
      next: (list) => { this.users = list; this.loading = false; },
      error: () => { this.loading = false; this.error = true; },
    });
  }
}
