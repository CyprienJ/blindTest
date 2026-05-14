import { Component, inject, NgZone, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TURNSTILE_SITE_KEY } from '../../../core/config';

declare const turnstile: {
  render: (container: string | HTMLElement, options: Record<string, unknown>) => string;
  reset: (widgetId: string) => void;
  remove: (widgetId: string) => void;
};

@Component({
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
          <h1 style="font-size: 20px; font-weight: 700; margin: 0 0 4px; letter-spacing: -0.01em;">
            Create an account
          </h1>
          <p style="color: var(--fg-2); font-size: 13.5px; margin: 0 0 24px;">Join the game</p>

          <form
            [formGroup]="form"
            (ngSubmit)="submit()"
            style="display: flex; flex-direction: column; gap: 16px;"
          >
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
              <label class="bt-label" for="email">Email</label>
              <input
                id="email"
                class="bt-input"
                type="email"
                formControlName="email"
                placeholder="you@example.com"
                autocomplete="email"
              />
              @if (form.get('email')?.invalid && form.get('email')?.touched) {
                <span class="bt-error">Enter a valid email address</span>
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
  private readonly zone = inject(NgZone);

  form: FormGroup = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(32)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });
  loading = false;
  showPassword = false;
  errorMessage = '';
  turnstileToken = '';
  private widgetId = '';

  ngOnInit(): void {
    setTimeout(() => this.initTurnstile(), 0);
  }

  ngOnDestroy(): void {
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
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.turnstileToken) {
      this.errorMessage = 'Please complete the security check.';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    const { username, email, password } = this.form.value as {
      username: string;
      email: string;
      password: string;
    };

    this.authService.register(username, email, password, this.turnstileToken).subscribe({
      next: () => {
        this.router.navigate(['/auth/verify-email'], {
          queryParams: { email, mode: 'check-inbox' },
        });
      },
      error: (err) => {
        this.loading = false;
        this.turnstileToken = '';
        if (this.widgetId && typeof turnstile !== 'undefined') {
          turnstile.reset(this.widgetId);
        }
        console.error('[register] error', err.status, err);
        if (err.status === 409) {
          const detail: string = err.error?.detail ?? '';
          this.errorMessage = detail.includes('EMAIL')
            ? 'Email already in use.'
            : 'Username already taken.';
        } else if (err.status === 403) {
          this.errorMessage = 'Security check failed. Please try again.';
        } else if (err.status === 400) {
          this.errorMessage = `Validation error: ${err.error?.detail ?? JSON.stringify(err.error)}`;
        } else if (err.status === 0) {
          this.errorMessage = 'Cannot reach the server — is the backend running?';
        } else {
          this.errorMessage = `Error ${err.status}: ${err.error?.detail ?? err.message ?? 'Unknown error'}`;
        }
      },
    });
  }

  private initTurnstile(): void {
    if (typeof turnstile === 'undefined') {
      setTimeout(() => this.initTurnstile(), 500);
      return;
    }
    this.widgetId = turnstile.render('#turnstile-container', {
      sitekey: TURNSTILE_SITE_KEY,
      theme: 'dark',
      callback: (token: string) => {
        this.zone.run(() => (this.turnstileToken = token));
      },
      'expired-callback': () => {
        this.zone.run(() => (this.turnstileToken = ''));
      },
    });
  }
}
