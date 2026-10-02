import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { LoginResponse, RegisterResponse } from '../../../core/models/user.model';
import { AuthService } from '../../../core/services/auth.service';
import { RegisterComponent } from './register.component';

interface TurnstileOptions {
  action?: string;
  callback?: (token: string) => void;
  'error-callback'?: () => boolean;
  'expired-callback'?: () => void;
  'refresh-expired'?: string;
  'response-field'?: boolean;
}

interface TurnstileMock {
  render: jasmine.Spy<(container: string | HTMLElement, options: TurnstileOptions) => string>;
  remove: jasmine.Spy<(widgetId: string) => void>;
  reset: jasmine.Spy<(widgetId: string) => void>;
}

describe('RegisterComponent', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;
  let turnstileMock: TurnstileMock;
  let turnstileOptions: TurnstileOptions = {};
  let inviteToken = '';

  beforeEach(async () => {
    turnstileOptions = {};
    turnstileMock = {
      render: jasmine.createSpy('render').and.callFake((_container, options) => {
        turnstileOptions = options;
        return 'test-widget';
      }),
      remove: jasmine.createSpy('remove'),
      reset: jasmine.createSpy('reset'),
    };
    Object.defineProperty(globalThis, 'turnstile', {
      configurable: true,
      value: turnstileMock,
    });
    authService = jasmine.createSpyObj<AuthService>('AuthService', [
      'lookupInvitation',
      'register',
      'login',
    ]);
    authService.lookupInvitation.and.returnValue(
      of({ email: 'musicfan@example.com', expiresAt: '2026-10-04T08:00:00Z' }),
    );
    inviteToken = 'invite-token';

    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useFactory: () => ({
            snapshot: {
              queryParamMap: convertToParamMap(inviteToken ? { invite: inviteToken } : {}),
            },
          }),
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    delete (globalThis as { turnstile?: TurnstileMock }).turnstile;
  });

  it('shows the invited address and consumes a Turnstile token once', () => {
    const registration = new Subject<RegisterResponse>();
    authService.register.and.returnValue(registration.asObservable());
    authService.login.and.returnValue(of({} as LoginResponse));
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(authService.lookupInvitation).toHaveBeenCalledOnceWith('invite-token');
    const email = fixture.nativeElement.querySelector('#email') as HTMLInputElement;
    expect(email.value).toBe('musicfan@example.com');
    expect(email.readOnly).toBeTrue();

    component.form.setValue({ username: 'musicfan', password: 'StrongPassword!' });
    component.turnstileToken = 'single-use-token';

    component.submit();
    component.submit();

    expect(authService.register).toHaveBeenCalledOnceWith(
      'musicfan',
      'invite-token',
      'StrongPassword!',
      'single-use-token',
    );
    expect(component.turnstileToken).toBe('');
    expect(component.loading).toBeTrue();

    registration.next({ id: 'id', username: 'musicfan', email: 'musicfan@example.com' });

    expect(authService.login).toHaveBeenCalledOnceWith('musicfan@example.com', 'StrongPassword!');
    expect(router.navigate).toHaveBeenCalledWith(['/home']);
  });

  it('switches to the unavailable state when registration reports an invalid invitation', () => {
    authService.register.and.returnValue(
      throwError(() => ({ status: 400, error: { code: 'INVITATION_INVALID' } })),
    );
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    component.form.setValue({ username: 'musicfan', password: 'StrongPassword!' });
    component.turnstileToken = 'token';

    component.submit();

    expect(component.invitationState).toBe('invalid');
    expect(component.loading).toBeFalse();
  });

  it('explains that registration is invitation-only without a usable invitation', () => {
    authService.lookupInvitation.and.returnValue(throwError(() => ({ status: 400 })));
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain(
      'Invitation unavailable',
    );
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('does not look up anything when the link carries no invitation', () => {
    inviteToken = '';
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();

    expect(authService.lookupInvitation).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Invitation only');
  });

  it('handles expired and failed challenges without exposing Cloudflare error codes', fakeAsync(() => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    tick();

    expect(turnstileOptions.action).toBe('register');
    expect(turnstileOptions['response-field']).toBeFalse();
    expect(turnstileOptions['refresh-expired']).toBe('auto');

    turnstileOptions.callback?.('fresh-token');
    expect(component.turnstileToken).toBe('fresh-token');

    turnstileOptions['expired-callback']?.();
    expect(component.turnstileToken).toBe('');
    expect(component.errorMessage).toBe('Security check expired. Please complete it again.');

    turnstileOptions.callback?.('replacement-token');
    expect(component.errorMessage).toBe('');
    expect(component.turnstileToken).toBe('replacement-token');

    const handled = turnstileOptions['error-callback']?.();
    expect(handled).toBeTrue();
    expect(component.turnstileToken).toBe('');
    expect(component.errorMessage).toBe('Security check could not be completed. Please try again.');

    fixture.destroy();
    expect(turnstileMock.remove).toHaveBeenCalledOnceWith('test-widget');
  }));
});
