import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

type State = 'form' | 'success' | 'error';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-reset-password',
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
          @if (state === 'form') {
            <h1
              style="font-size: 20px; font-weight: 700; margin: 0 0 4px; letter-spacing: -0.01em;"
            >
              Choose a new password
            </h1>
            <p style="color: var(--fg-2); font-size: 13.5px; margin: 0 0 24px;">
              Must be at least 8 characters.
            </p>

            <form
              [formGroup]="form"
              (ngSubmit)="submit()"
              style="display: flex; flex-direction: column; gap: 16px;"
            >
              <div class="bt-field">
                <label class="bt-label" for="newPassword">New password</label>
                <div style="position: relative;">
                  <input
                    id="newPassword"
                    class="bt-input"
                    [type]="showPassword ? 'text' : 'password'"
                    formControlName="newPassword"
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
                @if (form.get('newPassword')?.invalid && form.get('newPassword')?.touched) {
                  <span class="bt-error">At least 8 characters required</span>
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
                {{ loading ? 'Saving…' : 'Set new password' }}
              </button>
            </form>
          }

          @if (state === 'success') {
            <div style="text-align: center;">
              <div style="font-size: 40px; margin-bottom: 16px;">✅</div>
              <h1 style="font-size: 20px; font-weight: 700; margin: 0 0 8px;">Password updated!</h1>
              <p style="color: var(--fg-2); font-size: 14px; margin: 0 0 24px;">
                You can now sign in with your new password.
              </p>
              <a
                routerLink="/auth/login"
                class="bt-btn bt-btn-primary bt-btn-lg"
                style="width: 100%;"
              >
                Sign in
              </a>
            </div>
          }

          @if (state === 'error') {
            <div style="text-align: center;">
              <div style="font-size: 40px; margin-bottom: 16px;">❌</div>
              <h1 style="font-size: 20px; font-weight: 700; margin: 0 0 8px; color: var(--danger);">
                Invalid link
              </h1>
              <p style="color: var(--fg-2); font-size: 14px; margin: 0 0 24px;">
                This reset link is invalid or has expired.
              </p>
              <a
                routerLink="/auth/forgot-password"
                class="bt-btn bt-btn-ghost"
                style="width: 100%;"
              >
                Request a new link
              </a>
            </div>
          }
        </div>

        @if (state === 'form') {
          <p style="text-align: center; margin-top: 20px; font-size: 13.5px; color: var(--fg-2);">
            <a
              routerLink="/auth/login"
              style="color: var(--accent); text-decoration: none; font-weight: 600;"
            >
              ← Back to sign in
            </a>
          </p>
        }
      </div>
    </div>
  `,
})
export class ResetPasswordComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  form: FormGroup = this.fb.group({
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
  });
  loading = false;
  showPassword = false;
  errorMessage = '';
  state: State = 'form';
  private token = '';

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) {
      this.state = 'error';
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    const { newPassword } = this.form.value as { newPassword: string };

    this.authService.resetPassword(this.token, newPassword).subscribe({
      next: () => {
        this.loading = false;
        this.state = 'success';
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 400) {
          this.state = 'error';
        } else {
          this.errorMessage = 'Something went wrong. Please try again.';
        }
      },
    });
  }
}
