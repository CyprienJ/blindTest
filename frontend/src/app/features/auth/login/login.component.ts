import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LanguageSwitchComponent } from '../../../shared/language-switch/language-switch.component';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, LanguageSwitchComponent, ReactiveFormsModule, RouterLink],
  template: `
    <div class="bt-screen" style="justify-content: center; align-items: center; min-height: 100vh;">
      <div style="width: 100%; max-width: 400px; padding: 24px;">
        <div class="bt-logo" style="justify-content: center; margin-bottom: 32px;">
          <div class="bt-logo-mark"></div>
          <span>Blindtest</span>
        </div>
        <div style="display: flex; justify-content: center; margin: -16px 0 20px;">
          <app-language-switch />
        </div>

        <div class="bt-card">
          <h1
            i18n
            style="font-size: 20px; font-weight: 700; margin: 0 0 4px; letter-spacing: -0.01em;"
          >
            Welcome back
          </h1>
          <p i18n style="color: var(--fg-2); font-size: 13.5px; margin: 0 0 24px;">
            Sign in to your account
          </p>

          <form
            [formGroup]="form"
            (ngSubmit)="submit()"
            style="display: flex; flex-direction: column; gap: 16px;"
          >
            <div class="bt-field">
              <label class="bt-label" for="email" i18n>Email</label>
              <input
                id="email"
                class="bt-input"
                type="email"
                formControlName="email"
                i18n-placeholder
                placeholder="you@example.com"
                autocomplete="email"
              />
              @if (form.get('email')?.invalid && form.get('email')?.touched) {
                <span class="bt-error" i18n>Enter a valid email address</span>
              }
            </div>

            <div class="bt-field">
              <label class="bt-label" for="password" i18n>Password</label>
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
                  [attr.aria-label]="showPassword ? hidePasswordLabel : showPasswordLabel"
                >
                  {{ showPassword ? '🙈' : '👁' }}
                </button>
              </div>
              @if (form.get('password')?.invalid && form.get('password')?.touched) {
                <span class="bt-error" i18n>Password is required</span>
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
              @if (loading) {
                <ng-container i18n>Signing in…</ng-container>
              } @else {
                <ng-container i18n>Sign in</ng-container>
              }
            </button>
          </form>

          <div style="text-align: center; margin-top: 16px;">
            <a
              i18n
              routerLink="/auth/forgot-password"
              style="font-size: 13px; color: var(--fg-2); text-decoration: none;"
            >
              Forgot your password?
            </a>
          </div>
        </div>

        <p
          i18n
          style="text-align: center; margin-top: 20px; font-size: 13.5px; color: var(--fg-2);"
        >
          No account? Blindtest is invitation-only; ask an administrator for an invitation.
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
  readonly showPasswordLabel = $localize`Show password`;
  readonly hidePasswordLabel = $localize`Hide password`;
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
          this.errorMessage = $localize`Invalid email or password.`;
        } else if (err.status === 403) {
          this.errorMessage = $localize`Please verify your email before signing in.`;
        } else {
          this.errorMessage = $localize`Something went wrong. Please try again.`;
        }
      },
    });
  }
}
