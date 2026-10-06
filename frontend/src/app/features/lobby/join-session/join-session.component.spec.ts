import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { GuestSessionService } from '../../../core/services/guest-session.service';
import { JoinSessionComponent } from './join-session.component';

interface FakeTurnstile {
  render: jasmine.Spy;
  reset: jasmine.Spy;
  remove: jasmine.Spy;
  solve: (token: string) => void;
}

function installTurnstile(): FakeTurnstile {
  let callback: (token: string) => void = () => undefined;
  const fake: FakeTurnstile = {
    render: jasmine
      .createSpy('render')
      .and.callFake((_container: string, options: { callback: (token: string) => void }) => {
        callback = options.callback;
        return 'widget-1';
      }),
    reset: jasmine.createSpy('reset'),
    remove: jasmine.createSpy('remove'),
    solve: (token) => callback(token),
  };
  (window as unknown as { turnstile: FakeTurnstile }).turnstile = fake;
  return fake;
}

describe('JoinSessionComponent', () => {
  let http: HttpTestingController;

  function signIn(): void {
    localStorage.setItem('access_token', 'access-token');
    localStorage.setItem(
      'current_user',
      JSON.stringify({ id: 'u1', username: 'p', email: 'p@e.com', role: 'USER', avatarUrl: null }),
    );
  }

  async function create(code: string | null = null, signedIn = true) {
    if (signedIn) {
      signIn();
    }
    await TestBed.configureTestingModule({
      imports: [JoinSessionComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(code ? { code } : {}) },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(JoinSessionComponent);
    fixture.autoDetectChanges();
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  afterEach(() => {
    http.verify();
    localStorage.clear();
    sessionStorage.clear();
    delete (window as unknown as { turnstile?: FakeTurnstile }).turnstile;
  });

  function paste(element: HTMLElement, text: string): void {
    const clipboardData = new DataTransfer();
    clipboardData.setData('text', text);
    element
      .querySelector('input')!
      .dispatchEvent(new ClipboardEvent('paste', { clipboardData, bubbles: true }));
  }

  it('fills every cell from a pasted code and joins the lobby', async () => {
    const fixture = await create();
    const element = fixture.nativeElement as HTMLElement;
    const button = element.querySelector<HTMLButtonElement>('button')!;
    expect(button.textContent).toContain('Enter 6 more characters');

    paste(element, 'abc-234');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.code).toBe('ABC234');
    expect(button.disabled).toBeFalse();

    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    button.click();
    http.expectOne('/api/lobbies/ABC234/participants').flush({ code: 'ABC234' });
    expect(navigate).toHaveBeenCalledWith(['/lobby', 'ABC234']);
  });

  it('prefills a shared code and reports a full or unknown lobby', async () => {
    const fixture = await create('xyz789');
    expect(fixture.componentInstance.code).toBe('XYZ789');

    fixture.componentInstance.join();
    http
      .expectOne('/api/lobbies/XYZ789/participants')
      .flush({ code: 'LOBBY_FULL' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('This lobby is full (10 players).');

    fixture.componentInstance.join();
    http
      .expectOne('/api/lobbies/XYZ789/participants')
      .flush({ code: 'LOBBY_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No open lobby uses this code.');
  });

  it('ignores characters outside the code alphabet', async () => {
    const fixture = await create();
    paste(fixture.nativeElement, '0O1I');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.code).toBe('');
    expect(fixture.nativeElement.textContent).toContain('letters and the digits 2 to 9 only');
  });

  it('lets a visitor without an account join as a guest with a nickname', async () => {
    const turnstile = installTurnstile();
    const fixture = await create('abc234', false);
    await new Promise((resolve) => setTimeout(resolve));
    const element = fixture.nativeElement as HTMLElement;
    const button = element.querySelector<HTMLButtonElement>('button')!;
    expect(element.textContent).toContain('joining as a guest');
    expect(turnstile.render).toHaveBeenCalled();
    expect(button.textContent).toContain('Choose a nickname');

    const nickname = element.querySelector<HTMLInputElement>('input[name="nickname"]')!;
    nickname.value = ' Visitor ';
    nickname.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(button.textContent).toContain('Complete the security check');
    turnstile.solve('solved');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(button.disabled).toBeFalse();

    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    button.click();
    const request = http.expectOne('/api/lobbies/ABC234/guests');
    expect(request.request.body).toEqual({ nickname: 'Visitor', turnstileToken: 'solved' });
    request.flush({
      guestToken: 'guest-token',
      lobby: {
        sessionId: 'session-1',
        code: 'ABC234',
        participantId: 'guest-1',
        participants: [
          {
            participantId: 'guest-1',
            displayName: 'Visitor',
            ready: false,
            host: false,
            guest: true,
          },
        ],
      },
    });

    expect(TestBed.inject(GuestSessionService).get()).toEqual({
      token: 'guest-token',
      code: 'ABC234',
      sessionId: 'session-1',
      participantId: 'guest-1',
      nickname: 'Visitor',
    });
    expect(navigate).toHaveBeenCalledWith(['/lobby', 'ABC234']);
  });

  it('asks a guest for a fresh security check after a taken nickname', async () => {
    const turnstile = installTurnstile();
    const fixture = await create('abc234', false);
    await new Promise((resolve) => setTimeout(resolve));
    fixture.componentInstance.nickname = 'Host';
    turnstile.solve('solved');
    fixture.componentInstance.join();
    http
      .expectOne('/api/lobbies/ABC234/guests')
      .flush({ code: 'GUEST_NICKNAME_TAKEN' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('already uses this nickname');
    expect(turnstile.reset).toHaveBeenCalledWith('widget-1');
    expect(fixture.componentInstance.turnstileToken).toBe('');
  });
});
