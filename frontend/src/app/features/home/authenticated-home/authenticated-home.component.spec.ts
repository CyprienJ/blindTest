import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { UserSummary } from '../../../core/models/user.model';
import { AuthenticatedHomeComponent } from './authenticated-home.component';

describe('AuthenticatedHomeComponent', () => {
  let fixture: ComponentFixture<AuthenticatedHomeComponent>;

  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  async function render(user: UserSummary): Promise<HTMLElement> {
    localStorage.setItem('access_token', 'access-token');
    localStorage.setItem('current_user', JSON.stringify(user));
    await TestBed.configureTestingModule({
      imports: [AuthenticatedHomeComponent],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(AuthenticatedHomeComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows playable actions without administrator navigation to a player', async () => {
    const element = await render(user('USER'));

    expect(element.querySelector('h1')?.textContent).toContain('musicfan');
    expect(element.querySelector('a[href="/lobby/create"]')).not.toBeNull();
    expect(element.textContent).toContain('Join a game');
    expect(element.textContent).toContain('Enter the 6-character code your host shared.');
    expect(element.querySelector('a[href="/admin/playlists"]')).toBeNull();
  });

  it('shows playlist administration to an administrator', async () => {
    const element = await render(user('ADMIN'));

    expect(element.querySelector('a[href="/admin/playlists"]')?.textContent).toContain(
      'Edit playlists',
    );
  });
});

function user(role: string): UserSummary {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    username: 'musicfan',
    email: 'musicfan@example.com',
    role,
    avatarUrl: null,
  };
}
