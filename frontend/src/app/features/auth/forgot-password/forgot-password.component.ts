import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-forgot-password',
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
          @if (!sent) {
            <h1
              style="font-size: 20px; font-weight: 700; margin: 0 0 4px; letter-spacing: -0.01em;"
            >
              Reset your password
            </h1>
            <p style="color: var(--fg-2); font-size: 13.5px; margin: 0 0 24px;">
              Enter your email and we'll send you a reset link.
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

              <button
                type="submit"
                class="bt-btn bt-btn-primary bt-btn-lg"
                style="width: 100%;"
                [disabled]="loading"
              >
                {{ loading ? 'Sending…' : 'Send reset link' }}
              </button>
            </form>
          }

          @if (sent) {
            <div style="text-align: center;">
              <div style="font-size: 40px; margin-bottom: 16px;">📧</div>
              <h1 style="font-size: 20px; font-weight: 700; margin: 0 0 8px;">Check your inbox</h1>
              <p style="color: var(--fg-2); font-size: 14px; margin: 0 0 24px; line-height: 1.6;">
                If an account exists for that email, we've sent a reset link.
              </p>
            </div>
          }
        </div>

        <p style="text-align: center; margin-top: 20px; font-size: 13.5px; color: var(--fg-2);">
          <a
            routerLink="/auth/login"
            style="color: var(--accent); text-decoration: none; font-weight: 600;"
          >
            ← Back to sign in
          </a>
        </p>
      </div>
    </div>
  `,
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  form: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
  });
  loading = false;
  sent = false;

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    const { email } = this.form.value as { email: string };

    this.authService.forgotPassword(email).subscribe({
      next: () => {
        this.loading = false;
        this.sent = true;
      },
      error: () => {
        this.loading = false;
        this.sent = true;
      },
    });
  }
}
