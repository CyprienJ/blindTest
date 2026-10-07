import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PublicHomeComponent } from './public-home.component';

describe('PublicHomeComponent', () => {
  let fixture: ComponentFixture<PublicHomeComponent>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [PublicHomeComponent],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => localStorage.clear());

  it('offers sign-in, guest join, and explains invitation-only access to an anonymous visitor', () => {
    fixture = TestBed.createComponent(PublicHomeComponent);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h1')?.textContent).toContain('Hear it.');
    expect(element.querySelector('a[href="/auth/register"]')).toBeNull();
    expect(element.querySelector('a[href="/auth/login"]')?.textContent).toContain('Sign in');
    expect(element.textContent).toContain('invitation-only');
    expect(element.querySelector('a[href="/lobby/join"]')?.textContent).toContain('Join a game');
    expect(element.querySelector('a[href="/home"]')).toBeNull();
  });

  it('offers the authenticated entry point when a complete local session exists', () => {
    localStorage.setItem('access_token', 'access-token');
    localStorage.setItem(
      'current_user',
      JSON.stringify({
        id: '11111111-1111-4111-8111-111111111111',
        username: 'musicfan',
        email: 'musicfan@example.com',
        role: 'USER',
        avatarUrl: null,
      }),
    );

    fixture = TestBed.createComponent(PublicHomeComponent);
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a[href="/home"]') as HTMLAnchorElement;
    expect(link.textContent).toContain('Continue as musicfan');
    expect(fixture.nativeElement.querySelector('a[href="/auth/register"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/lobby/join"]')).toBeNull();
  });
});
