import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { RegisterResponse } from '../../../core/models/user.model';
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
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['register']);

    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [{ provide: AuthService, useValue: authService }, provideRouter([])],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    delete (globalThis as { turnstile?: TurnstileMock }).turnstile;
  });

  it('consumes a Turnstile token once and ignores a duplicate submission', () => {
    const registration = new Subject<RegisterResponse>();
    authService.register.and.returnValue(registration.asObservable());
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    component.form.setValue({
      username: 'musicfan',
      email: 'musicfan@example.com',
      password: 'StrongPassword!',
    });
    component.turnstileToken = 'single-use-token';

    component.submit();
    component.submit();

    expect(authService.register).toHaveBeenCalledOnceWith(
      'musicfan',
      'musicfan@example.com',
      'StrongPassword!',
      'single-use-token',
    );
    expect(component.turnstileToken).toBe('');
    expect(component.loading).toBeTrue();

    registration.error({ status: 500 });

    expect(component.loading).toBeFalse();
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
