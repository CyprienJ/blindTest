import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { UserSummary } from '../../models/user.model';
import { AuthService } from '../../services/auth.service';
import { AppShellComponent } from './app-shell.component';

function user(role: string): UserSummary {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    username: 'musicfan',
    email: 'musicfan@example.com',
    role,
    avatarUrl: null,
  };
}

describe('AppShellComponent', () => {
  let fixture: ComponentFixture<AppShellComponent>;

  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  async function render(role: string | null): Promise<HTMLElement> {
    if (role) {
      localStorage.setItem('access_token', 'access-token');
      localStorage.setItem('current_user', JSON.stringify(user(role)));
    }
    await TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('exposes the primary navigation with disabled placeholder items', async () => {
    const element = await render('USER');

    const nav = element.querySelector('nav[aria-label="Primary navigation"]');
    expect(nav).not.toBeNull();
    expect(nav?.querySelector('a[href="/lobby/create"]')?.textContent).toContain('Play');

    expect(nav?.querySelector('a[href="/lobby/create#public-playlists"]')).not.toBeNull();

    const disabled = Array.from(element.querySelectorAll('.bt-nav-item[aria-disabled="true"]')).map(
      (item) => item.textContent?.trim(),
    );
    expect(disabled).toEqual(['Friends', 'History']);
    expect(element.querySelector('a[href="/friends"]')).toBeNull();
  });

  it('hides the administration link from a player', async () => {
    const element = await render('USER');
    expect(element.querySelector('a[href="/admin"]')).toBeNull();
  });

  it('shows the administration link to an administrator', async () => {
    const element = await render('ADMIN');
    expect(element.querySelector('a[href="/admin"]')?.textContent).toContain('Admin');
  });

  it('renders the account cluster and signs out through the auth service', async () => {
    const element = await render('USER');
    const auth = TestBed.inject(AuthService);
    const logout = spyOn(auth, 'logout').and.returnValue(of(void 0));

    expect(element.querySelector('.bt-account-name')?.textContent).toContain('musicfan');
    expect(element.querySelector('.bt-avatar')?.textContent?.trim()).toBe('M');

    const button = element.querySelector('.bt-topbar-account button') as HTMLButtonElement;
    expect(button.textContent).toContain('Sign out');
    button.click();

    expect(logout).toHaveBeenCalled();
  });

  it('removes privileged navigation when the session is cleared', async () => {
    const element = await render('ADMIN');
    TestBed.inject(AuthService).clearSession();
    fixture.detectChanges();
    expect(element.querySelector('a[href="/admin"]')).toBeNull();
    expect(element.querySelector('.bt-topbar-account')).toBeNull();
  });

  it('prevents duplicate logout requests while sign-out is pending', async () => {
    await render('USER');
    const pending = new Subject<void>();
    const logout = spyOn(TestBed.inject(AuthService), 'logout').and.returnValue(pending);
    fixture.componentInstance.signOut();
    fixture.componentInstance.signOut();
    fixture.detectChanges();
    expect(logout).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('button').disabled).toBeTrue();
    pending.complete();
  });

  it('omits the account cluster when no session is restored', async () => {
    const element = await render(null);
    expect(element.querySelector('.bt-topbar-account')).toBeNull();
    expect(element.querySelector('nav[aria-label="Primary navigation"]')).not.toBeNull();
  });
});
