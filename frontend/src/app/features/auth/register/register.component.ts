import {
  ChangeDetectionStrategy,
  Component,
  inject,
  NgZone,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TURNSTILE_SITE_KEY } from '../../../core/config';

declare const turnstile: {
  render: (container: string | HTMLElement, options: Record<string, unknown>) => string;
  reset: (widgetId: string) => void;
  remove: (widgetId: string) => void;
};

type InvitationState = 'checking' | 'ready' | 'invalid' | 'missing';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="bt-screen" style="justify-content: center; align-items: center; min-height: 100vh;">
      <div style="width: 100%; max-width: 420px; padding: 24px;">
        <div class="bt-logo" style="justify-content: center; margin-bottom: 32px;">
          <div class="bt-logo-mark"></div>
          <span>Blindtest</span>
        </div>

        <div class="bt-card">
          @switch (invitationState) {
            @case ('checking') {
              <p role="status" style="color: var(--fg-2); font-size: 13.5px; margin: 0;">
                Checking your invitation…
              </p>
            }
            @case ('ready') {
              <h1
                style="font-size: 20px; font-weight: 700; margin: 0 0 4px; letter-spacing: -0.01em;"
              >
                Create an account
              </h1>
              <p style="color: var(--fg-2); font-size: 13.5px; margin: 0 0 24px;">
                You're invited to join the game
              </p>

              <form
                [formGroup]="form"
                (ngSubmit)="submit()"
                style="display: flex; flex-direction: column; gap: 16px;"
              >
                <div class="bt-field">
                  <label class="bt-label" for="email">Email</label>
                  <input
                    id="email"
                    class="bt-input"
                    type="email"
                    [value]="invitedEmail"
                    readonly
                    autocomplete="email"
                  />
                </div>

                <div class="bt-field">
                  <label class="bt-label" for="username">Username</label>
                  <input
                    id="username"
                    class="bt-input"
                    type="text"
                    formControlName="username"
                    placeholder="coolplayer42"
                    autocomplete="username"
                  />
                  @if (form.get('username')?.invalid && form.get('username')?.touched) {
                    <span class="bt-error">3–32 characters required</span>
                  }
                </div>

                <div class="bt-field">
                  <label class="bt-label" for="password">Password</label>
                  <div style="position: relative;">
                    <input
                      id="password"
                      class="bt-input"
                      [type]="showPassword ? 'text' : 'password'"
                      formControlName="password"
                      placeholder="••••••••"
                      autocomplete="new-password"
                      style="padding-right: 44px;"
                    />
                    <button
                      type="button"
                      (click)="showPassword = !showPassword"
                      style="position: absolute; right: 12px; top: 50%; transform: translateY(-50%);
                             background: none; border: none; cursor: pointer; color: var(--fg-2); padding: 0;"
                      [attr.aria-label]="showPassword ? 'Hide password' : 'Show password'"
                    >
                      {{ showPassword ? '🙈' : '👁' }}
                    </button>
                  </div>
                  @if (form.get('password')?.valid && form.value.password) {
                    <div
                      style="height: 4px; background: var(--bg-3); border-radius: 2px; overflow: hidden;"
                    >
                      <div
                        [style.width]="passwordStrengthPercent + '%'"
                        [style.background]="passwordStrengthColor"
                        style="height: 100%; border-radius: 2px; transition: width .2s, background .2s;"
                      ></div>
                    </div>
                  }
                  @if (form.get('password')?.invalid && form.get('password')?.touched) {
                    <span class="bt-error">At least 8 characters required</span>
                  }
                </div>

                <div id="turnstile-container" style="min-height: 65px;"></div>

                @if (errorMessage) {
                  <div
                    role="alert"
                    style="background: oklch(0.25 0.04 30); border: 1px solid oklch(0.38 0.10 30);
                              border-radius: var(--r-md); padding: 10px 14px; font-size: 13.5px; color: var(--danger);"
                  >
                    {{ errorMessage }}
                  </div>
                }

                <button
                  type="submit"
                  class="bt-btn bt-btn-primary bt-btn-lg"
                  style="width: 100%;"
                  [disabled]="loading || !turnstileToken"
                >
                  {{ loading ? 'Creating account…' : 'Create account' }}
                </button>
              </form>
            }
            @default {
              <h1
                style="font-size: 20px; font-weight: 700; margin: 0 0 8px; letter-spacing: -0.01em;"
              >
                {{ invitationState === 'invalid' ? 'Invitation unavailable' : 'Invitation only' }}
              </h1>
              <p style="color: var(--fg-2); font-size: 13.5px; line-height: 1.55; margin: 0;">
                @if (invitationState === 'invalid') {
                  This invitation is invalid, has expired, or was already used. Ask an administrator
                  for a new one.
                } @else {
                  Blindtest is invitation-only. Open the link from your invitation email to create
                  an account.
                }
              </p>
            }
          }
        </div>

        <p style="text-align: center; margin-top: 20px; font-size: 13.5px; color: var(--fg-2);">
          Already have an account?
          <a
            routerLink="/auth/login"
            style="color: var(--accent); text-decoration: none; font-weight: 600;"
          >
            Sign in
          </a>
        </p>
      </div>
    </div>
  `,
})
export class RegisterComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly zone = inject(NgZone);

  form: FormGroup = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(32)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });
  invitationState: InvitationState = 'checking';
  invitedEmail = '';
  loading = false;
  showPassword = false;
  errorMessage = '';
  turnstileToken = '';
  private inviteToken = '';
  private widgetId = '';
  private turnstileErrorActive = false;
  private destroyed = false;

  ngOnInit(): void {
    this.inviteToken = this.route.snapshot.queryParamMap.get('invite') ?? '';
    if (!this.inviteToken) {
      this.invitationState = 'missing';
      return;
    }
    this.authService.lookupInvitation(this.inviteToken).subscribe({
      next: (invitation) => {
        this.invitedEmail = invitation.email;
        this.invitationState = 'ready';
        setTimeout(() => this.initTurnstile(), 0);
      },
      error: () => {
        this.invitationState = 'invalid';
      },
    });
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    if (this.widgetId && typeof turnstile !== 'undefined') {
      turnstile.remove(this.widgetId);
    }
  }

  get passwordStrengthPercent(): number {
    const pw: string = this.form.value.password ?? '';
    let score = 0;
    if (pw.length >= 8) score += 25;
    if (pw.length >= 12) score += 25;
    if (/[A-Z]/.test(pw)) score += 25;
    if (/[^A-Za-z0-9]/.test(pw)) score += 25;
    return score;
  }

  get passwordStrengthColor(): string {
    const pct = this.passwordStrengthPercent;
    if (pct <= 25) return 'var(--danger)';
    if (pct <= 50) return 'var(--warn)';
    return 'var(--accent)';
  }

  submit(): void {
    if (this.loading) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.turnstileToken) {
      this.showTurnstileError('Please complete the security check.');
      return;
    }
    const turnstileToken = this.turnstileToken;
    this.turnstileToken = '';
    this.loading = true;
    this.errorMessage = '';
    this.turnstileErrorActive = false;
    const { username, password } = this.form.value as { username: string; password: string };

    this.authService.register(username, this.inviteToken, password, turnstileToken).subscribe({
      next: (account) => this.signIn(account.email, password),
      error: (err) => {
        this.loading = false;
        this.resetTurnstile();
        this.turnstileErrorActive = false;
        const code: string = err.error?.code ?? err.error?.detail ?? '';
        if (err.status === 409) {
          this.errorMessage = code.includes('EMAIL')
            ? 'An account already exists for this email. Sign in instead.'
            : 'Username already taken.';
        } else if (err.status === 403) {
          this.errorMessage = 'Security check failed. Please try again.';
          this.turnstileErrorActive = true;
        } else if (err.status === 400 && code === 'INVITATION_INVALID') {
          this.invitationState = 'invalid';
        } else if (err.status === 400) {
          this.errorMessage = 'Please check the form and try again.';
        } else if (err.status === 0) {
          this.errorMessage = 'Cannot reach the server — is the backend running?';
        } else {
          this.errorMessage = 'Something went wrong. Please try again.';
        }
      },
    });
  }

  /** The invitation proved the address, so the new account can sign in immediately. */
  private signIn(email: string, password: string): void {
    this.authService.login(email, password).subscribe({
      next: () => this.router.navigate(['/home']),
      error: () => this.router.navigate(['/auth/login']),
    });
  }

  private initTurnstile(): void {
    if (this.destroyed || this.invitationState !== 'ready') {
      return;
    }
    if (typeof turnstile === 'undefined') {
      setTimeout(() => this.initTurnstile(), 500);
      return;
    }
    this.widgetId = turnstile.render('#turnstile-container', {
      sitekey: TURNSTILE_SITE_KEY,
      theme: 'dark',
      action: 'register',
      'response-field': false,
      'refresh-expired': 'auto',
      callback: (token: string) => {
        this.zone.run(() => {
          this.turnstileToken = token;
          if (this.turnstileErrorActive) {
            this.errorMessage = '';
            this.turnstileErrorActive = false;
          }
        });
      },
      'expired-callback': () => {
        this.zone.run(() => {
          this.showTurnstileError('Security check expired. Please complete it again.');
        });
      },
      'error-callback': (): boolean => {
        this.zone.run(() => {
          this.showTurnstileError('Security check could not be completed. Please try again.');
        });
        return true;
      },
    });
  }

  private resetTurnstile(): void {
    this.turnstileToken = '';
    if (this.widgetId && typeof turnstile !== 'undefined') {
      turnstile.reset(this.widgetId);
    }
  }

  private showTurnstileError(message: string): void {
    this.turnstileToken = '';
    this.errorMessage = message;
    this.turnstileErrorActive = true;
  }
}
