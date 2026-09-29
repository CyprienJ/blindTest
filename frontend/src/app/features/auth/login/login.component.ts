import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="bt-screen" style="justify-content: center; align-items: center; min-height: 100vh;">
      <div style="width: 100%; max-width: 400px; padding: 24px;">
        <div class="bt-logo" style="justify-content: center; margin-bottom: 32px;">
          <div class="bt-logo-mark"></div>
          <span>Blindtest</span>
        </div>

        <div class="bt-card">
          <h1 style="font-size: 20px; font-weight: 700; margin: 0 0 4px; letter-spacing: -0.01em;">
            Welcome back
          </h1>
          <p style="color: var(--fg-2); font-size: 13.5px; margin: 0 0 24px;">
            Sign in to your account
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
                  autocomplete="current-password"
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
              @if (form.get('password')?.invalid && form.get('password')?.touched) {
                <span class="bt-error">Password is required</span>
              }
            </div>

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
              [disabled]="loading"
            >
              {{ loading ? 'Signing in…' : 'Sign in' }}
            </button>
          </form>

          <div style="text-align: center; margin-top: 16px;">
            <a
              routerLink="/auth/forgot-password"
              style="font-size: 13px; color: var(--fg-2); text-decoration: none;"
            >
              Forgot your password?
            </a>
          </div>
        </div>

        <p style="text-align: center; margin-top: 20px; font-size: 13.5px; color: var(--fg-2);">
          No account?
          <a
            routerLink="/auth/register"
            style="color: var(--accent); text-decoration: none; font-weight: 600;"
          >
            Sign up
          </a>
        </p>
      </div>
    </div>
  `,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  form: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  loading = false;
  showPassword = false;
  errorMessage = '';

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    const { email, password } = this.form.value as { email: string; password: string };

    this.authService.login(email, password).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/home';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 401) {
          this.errorMessage = 'Invalid email or password.';
        } else if (err.status === 403) {
          this.errorMessage = 'Please verify your email before signing in.';
        } else {
          this.errorMessage = 'Something went wrong. Please try again.';
        }
      },
    });
  }
}
