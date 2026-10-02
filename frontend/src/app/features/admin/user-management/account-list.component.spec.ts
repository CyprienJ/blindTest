import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { UserSummary } from '../../../core/models/user.model';
import { AuthService } from '../../../core/services/auth.service';
import { AccountListComponent } from './account-list.component';
import { AdminUser } from './admin-user.model';
import { AdminUserService } from './admin-user.service';

function user(overrides: Partial<AdminUser>): AdminUser {
  return {
    id: 'id',
    username: 'name',
    email: 'name@example.com',
    role: 'USER',
    emailVerified: true,
    createdAt: '2026-09-01T10:00:00Z',
    lastConnexion: null,
    gamesPlayed: 0,
    playlistCount: 0,
    ...overrides,
  };
}

const ADMIN = user({
  id: 'admin-id',
  username: 'admin',
  email: 'admin@example.com',
  role: 'ADMIN',
  createdAt: '2026-08-01T10:00:00Z',
  gamesPlayed: 3,
  playlistCount: 2,
});
const ALICE = user({
  id: 'alice-id',
  username: 'alice',
  email: 'alice@example.com',
  createdAt: '2026-09-20T10:00:00Z',
  lastConnexion: '2026-10-01T10:00:00Z',
  gamesPlayed: 12,
});
const BOB = user({
  id: 'bob-id',
  username: 'bob',
  email: 'bob@example.com',
  emailVerified: false,
  createdAt: '2026-09-10T10:00:00Z',
  gamesPlayed: 1,
});

describe('AccountListComponent', () => {
  let service: jasmine.SpyObj<AdminUserService>;
  let fixture: ComponentFixture<AccountListComponent>;
  let component: AccountListComponent;

  beforeEach(async () => {
    service = jasmine.createSpyObj<AdminUserService>('AdminUserService', [
      'list',
      'update',
      'delete',
    ]);
    service.list.and.returnValue(of([ADMIN, ALICE, BOB]));
    const currentUser = new BehaviorSubject<UserSummary | null>({
      id: 'admin-id',
      username: 'admin',
      email: 'admin@example.com',
      role: 'ADMIN',
      avatarUrl: null,
    });

    await TestBed.configureTestingModule({
      imports: [AccountListComponent],
      providers: [
        { provide: AdminUserService, useValue: service },
        { provide: AuthService, useValue: { currentUser$: currentUser.asObservable() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function usernames(): string[] {
    return component.visibleUsers.map((account) => account.username);
  }

  it('lists accounts newest first with summary counts', () => {
    expect(usernames()).toEqual(['alice', 'bob', 'admin']);
    expect(component.stats.map((stat) => stat.value).slice(0, 3)).toEqual([3, 2, 1]);
    expect(element().querySelectorAll('tbody tr').length).toBe(3);
  });

  it('sorts by any column and toggles the direction', () => {
    component.sortBy('gamesPlayed');
    expect(usernames()).toEqual(['bob', 'admin', 'alice']);
    component.sortBy('gamesPlayed');
    expect(usernames()).toEqual(['alice', 'admin', 'bob']);

    component.sortBy('username');
    expect(usernames()).toEqual(['admin', 'alice', 'bob']);

    component.sortBy('lastConnexion');
    expect(usernames()[0]).toBe('alice');
    fixture.detectChanges();
    expect(element().querySelector('th[aria-sort="descending"]')?.textContent).toContain(
      'Last sign-in',
    );
  });

  it('filters by role, verification, and search text', () => {
    component.filter = 'ADMIN';
    expect(usernames()).toEqual(['admin']);
    component.filter = 'UNVERIFIED';
    expect(usernames()).toEqual(['bob']);
    component.filter = 'ALL';
    component.search = 'ALICE@';
    expect(usernames()).toEqual(['alice']);
  });

  it('edits an account and replaces it in the list', () => {
    service.update.and.returnValue(of({ ...ALICE, username: 'alicia', role: 'ADMIN' }));

    component.openEdit(ALICE);
    component.editForm.setValue({ username: 'alicia', email: 'alice@example.com', role: 'ADMIN' });
    component.saveEdit();
    fixture.detectChanges();

    expect(service.update).toHaveBeenCalledOnceWith('alice-id', {
      username: 'alicia',
      email: 'alice@example.com',
      role: 'ADMIN',
    });
    expect(component.editing).toBeNull();
    expect(usernames()).toContain('alicia');
    expect(element().textContent).toContain('will get the new role at the next sign-in');
  });

  it('keeps the edit dialog open with a readable error on conflict', () => {
    service.update.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'USERNAME_TAKEN' } })),
    );

    component.openEdit(ALICE);
    component.saveEdit();

    expect(component.editing).toBe(ALICE);
    expect(component.editError).toBe('This username is already taken.');
  });

  it('locks the role of the signed-in administrator and prevents self-deletion', () => {
    component.openEdit(ADMIN);
    expect(component.editForm.controls.role.disabled).toBeTrue();
    component.closeEdit();
    fixture.detectChanges();

    const deleteSelf = element().querySelector<HTMLButtonElement>(
      'button[aria-label="Delete admin"]',
    );
    expect(deleteSelf?.disabled).toBeTrue();
  });

  it('deletes an account after confirmation', () => {
    service.delete.and.returnValue(of(undefined));

    element().querySelector<HTMLButtonElement>('button[aria-label="Delete bob"]')!.click();
    fixture.detectChanges();
    expect(element().querySelector('[role="alertdialog"]')?.textContent).toContain('Delete bob?');

    component.confirmDelete();
    fixture.detectChanges();

    expect(service.delete).toHaveBeenCalledOnceWith('bob-id');
    expect(usernames()).toEqual(['alice', 'admin']);
    expect(element().textContent).toContain('bob was deleted.');
  });
});
