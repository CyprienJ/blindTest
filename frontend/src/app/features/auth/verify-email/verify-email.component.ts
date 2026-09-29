import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

type State = 'check-inbox' | 'verifying' | 'success' | 'error';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="bt-screen" style="justify-content: center; align-items: center; min-height: 100vh;">
      <div style="width: 100%; max-width: 400px; padding: 24px;">
        <div class="bt-logo" style="justify-content: center; margin-bottom: 32px;">
          <div class="bt-logo-mark"></div>
          <span>Blindtest</span>
        </div>

        <div class="bt-card" style="text-align: center;">
          @if (state === 'check-inbox') {
            <div style="font-size: 40px; margin-bottom: 16px;">📬</div>
            <h1 style="font-size: 20px; font-weight: 700; margin: 0 0 8px;">Check your inbox</h1>
            <p style="color: var(--fg-2); font-size: 14px; margin: 0 0 24px; line-height: 1.6;">
              We sent a verification link to
              @if (email) {
                <strong style="color: var(--fg-0);">{{ email }}</strong>
              }
              . Click it to activate your account.
            </p>
            <p style="color: var(--fg-3); font-size: 13px; margin: 0 0 20px;">
              Didn't get it? Check your spam folder.
            </p>
            <a routerLink="/auth/login" class="bt-btn bt-btn-ghost" style="width: 100%;">
              Back to sign in
            </a>
          }

          @if (state === 'verifying') {
            <div style="font-size: 40px; margin-bottom: 16px;">⏳</div>
            <h1 style="font-size: 20px; font-weight: 700; margin: 0 0 8px;">Verifying…</h1>
            <p style="color: var(--fg-2); font-size: 14px; margin: 0;">
              Please wait while we verify your email.
            </p>
          }

          @if (state === 'success') {
            <div style="font-size: 40px; margin-bottom: 16px;">✅</div>
            <h1 style="font-size: 20px; font-weight: 700; margin: 0 0 8px;">Email verified!</h1>
            <p style="color: var(--fg-2); font-size: 14px; margin: 0 0 24px;">
              Your account is active. You can now sign in.
            </p>
            <a
              routerLink="/auth/login"
              class="bt-btn bt-btn-primary bt-btn-lg"
              style="width: 100%;"
            >
              Sign in
            </a>
          }

          @if (state === 'error') {
            <div style="font-size: 40px; margin-bottom: 16px;">❌</div>
            <h1 style="font-size: 20px; font-weight: 700; margin: 0 0 8px; color: var(--danger);">
              Invalid link
            </h1>
            <p style="color: var(--fg-2); font-size: 14px; margin: 0 0 24px;">
              This verification link is invalid or has expired.
            </p>
            <a routerLink="/auth/login" class="bt-btn bt-btn-ghost" style="width: 100%;">
              Back to sign in
            </a>
          }
        </div>
      </div>
    </div>
  `,
})
export class VerifyEmailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly authService = inject(AuthService);

  state: State = 'check-inbox';
  email = '';

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    const mode = this.route.snapshot.queryParamMap.get('mode');
    this.email = this.route.snapshot.queryParamMap.get('email') ?? '';

    if (token) {
      this.state = 'verifying';
      this.authService.verifyEmail(token).subscribe({
        next: () => (this.state = 'success'),
        error: () => (this.state = 'error'),
      });
    } else if (mode === 'check-inbox') {
      this.state = 'check-inbox';
    }
  }
}
