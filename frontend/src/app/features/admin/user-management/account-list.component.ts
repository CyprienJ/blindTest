import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  FormControl,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { take } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { AdminUser, UserRole } from './admin-user.model';
import { AdminUserService } from './admin-user.service';

export type AccountSortKey =
  | 'username'
  | 'email'
  | 'role'
  | 'createdAt'
  | 'lastConnexion'
  | 'gamesPlayed';
export type AccountFilter = 'ALL' | 'USER' | 'ADMIN' | 'UNVERIFIED';

const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-account-list',
  standalone: true,
  imports: [DatePipe, FormsModule, ReactiveFormsModule],
  templateUrl: './account-list.component.html',
  styleUrl: './account-list.component.scss',
})
export class AccountListComponent implements OnInit {
  private readonly userService = inject(AdminUserService);
  private readonly authService = inject(AuthService);

  readonly filters: { value: AccountFilter; label: string }[] = [
    { value: 'ALL', label: $localize`All` },
    { value: 'USER', label: $localize`Players` },
    { value: 'ADMIN', label: $localize`Admins` },
    { value: 'UNVERIFIED', label: $localize`Unverified` },
  ];
  readonly columns: { key: AccountSortKey; label: string }[] = [
    { key: 'username', label: $localize`User` },
    { key: 'email', label: $localize`Email` },
    { key: 'role', label: $localize`Role` },
    { key: 'createdAt', label: $localize`Joined` },
    { key: 'lastConnexion', label: $localize`Last sign-in` },
    { key: 'gamesPlayed', label: $localize`Games` },
  ];

  users: AdminUser[] = [];
  currentUserId: string | null = null;
  loading = true;
  listError = '';
  successMessage = '';
  search = '';
  readonly selfDeleteTitle = $localize`You cannot delete your own account`;
  filter: AccountFilter = 'ALL';
  sortKey: AccountSortKey = 'createdAt';
  sortDirection: 'asc' | 'desc' = 'desc';

  editing: AdminUser | null = null;
  editError = '';
  saving = false;
  readonly editForm = new FormGroup({
    username: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3), Validators.maxLength(50)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(255)],
    }),
    role: new FormControl<UserRole>('USER', { nonNullable: true }),
  });

  deleting: AdminUser | null = null;
  deleteError = '';
  deleteBusy = false;

  ngOnInit(): void {
    this.authService.currentUser$.pipe(take(1)).subscribe((user) => {
      this.currentUserId = user?.id ?? null;
    });
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.listError = '';
    this.userService.list().subscribe({
      next: (users) => {
        this.users = users;
        this.loading = false;
      },
      error: () => {
        this.listError = $localize`Accounts could not be loaded.`;
        this.loading = false;
      },
    });
  }

  get visibleUsers(): AdminUser[] {
    const query = this.search.trim().toLowerCase();
    const direction = this.sortDirection === 'asc' ? 1 : -1;
    return this.users
      .filter((user) => this.matchesFilter(user))
      .filter(
        (user) =>
          !query ||
          user.username.toLowerCase().includes(query) ||
          user.email.toLowerCase().includes(query),
      )
      .sort((left, right) => direction * this.compare(left, right));
  }

  get stats(): { label: string; value: number }[] {
    const weekAgo = Date.now() - WEEK_MS;
    return [
      { label: $localize`Total users`, value: this.users.length },
      { label: $localize`Verified`, value: this.users.filter((user) => user.emailVerified).length },
      {
        label: $localize`Admins`,
        value: this.users.filter((user) => user.role === 'ADMIN').length,
      },
      {
        label: $localize`New this week`,
        value: this.users.filter((user) => Date.parse(user.createdAt) >= weekAgo).length,
      },
    ];
  }

  sortBy(key: AccountSortKey): void {
    if (this.sortKey === key) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
      return;
    }
    this.sortKey = key;
    this.sortDirection = key === 'createdAt' || key === 'lastConnexion' ? 'desc' : 'asc';
  }

  ariaSort(key: AccountSortKey): 'ascending' | 'descending' | 'none' {
    if (this.sortKey !== key) {
      return 'none';
    }
    return this.sortDirection === 'asc' ? 'ascending' : 'descending';
  }

  isSelf(user: AdminUser): boolean {
    return user.id === this.currentUserId;
  }

  openEdit(user: AdminUser): void {
    this.editing = user;
    this.editError = '';
    this.successMessage = '';
    this.editForm.reset({ username: user.username, email: user.email, role: user.role });
    if (this.isSelf(user)) {
      this.editForm.controls.role.disable();
    } else {
      this.editForm.controls.role.enable();
    }
  }

  closeEdit(): void {
    this.editing = null;
  }

  saveEdit(): void {
    const user = this.editing;
    if (!user || this.saving) {
      return;
    }
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const value = this.editForm.getRawValue();
    this.saving = true;
    this.editError = '';
    this.userService
      .update(user.id, {
        username: value.username.trim(),
        email: value.email.trim(),
        role: value.role,
      })
      .subscribe({
        next: (updated) => {
          this.saving = false;
          this.users = this.users.map((candidate) =>
            candidate.id === updated.id ? updated : candidate,
          );
          this.editing = null;
          this.successMessage =
            updated.role !== user.role
              ? $localize`${updated.username} was updated and will get the new role at the next sign-in.`
              : $localize`${updated.username} was updated.`;
        },
        error: (error: HttpErrorResponse) => {
          this.saving = false;
          this.editError = this.describeError(error);
        },
      });
  }

  openDelete(user: AdminUser): void {
    this.deleting = user;
    this.deleteError = '';
    this.successMessage = '';
  }

  closeDelete(): void {
    this.deleting = null;
  }

  confirmDelete(): void {
    const user = this.deleting;
    if (!user || this.deleteBusy) {
      return;
    }
    this.deleteBusy = true;
    this.deleteError = '';
    this.userService.delete(user.id).subscribe({
      next: () => {
        this.deleteBusy = false;
        this.users = this.users.filter((candidate) => candidate.id !== user.id);
        this.deleting = null;
        this.successMessage = $localize`${user.username} was deleted.`;
      },
      error: (error: HttpErrorResponse) => {
        this.deleteBusy = false;
        this.deleteError = this.describeError(error);
      },
    });
  }

  private matchesFilter(user: AdminUser): boolean {
    switch (this.filter) {
      case 'USER':
      case 'ADMIN':
        return user.role === this.filter;
      case 'UNVERIFIED':
        return !user.emailVerified;
      default:
        return true;
    }
  }

  private compare(left: AdminUser, right: AdminUser): number {
    switch (this.sortKey) {
      case 'gamesPlayed':
        return left.gamesPlayed - right.gamesPlayed;
      case 'createdAt':
        return Date.parse(left.createdAt) - Date.parse(right.createdAt);
      case 'lastConnexion':
        // Accounts that never signed in sort as the oldest.
        return (
          (left.lastConnexion ? Date.parse(left.lastConnexion) : 0) -
          (right.lastConnexion ? Date.parse(right.lastConnexion) : 0)
        );
      default:
        return left[this.sortKey].localeCompare(right[this.sortKey]);
    }
  }

  private describeError(error: HttpErrorResponse): string {
    switch (error.error?.code) {
      case 'USERNAME_TAKEN':
        return $localize`This username is already taken.`;
      case 'EMAIL_TAKEN':
        return $localize`Another account already uses this email.`;
      case 'ADMIN_SELF_CHANGE_FORBIDDEN':
        return $localize`You cannot delete your own account or change your own role.`;
      case 'USER_OWNS_PLAYLISTS':
        return $localize`This user still owns playlists. Delete their playlists first.`;
      default:
        return error.status === 404
          ? $localize`This account no longer exists. Refresh the list.`
          : $localize`The account could not be updated. Please try again.`;
    }
  }
}
