import { LanguageSwitchComponent } from '../../../shared/language-switch/language-switch.component';
import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { GuestSessionService } from '../../services/guest-session.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-shell',
  standalone: true,
  imports: [AsyncPipe, LanguageSwitchComponent, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly guestSession = inject(GuestSessionService);

  readonly currentUser$ = this.authService.currentUser$;
  signingOut = false;

  /** A guest tab's nickname; guests see no account navigation. */
  get guestNickname(): string | null {
    return this.guestSession.get()?.nickname ?? null;
  }

  signOut(): void {
    if (this.signingOut) return;
    this.signingOut = true;
    this.changeDetector.markForCheck();
    this.authService.logout().subscribe({
      next: () => this.finishSignOut(),
      error: () => this.finishSignOut(),
    });
  }

  initials(username: string): string {
    return (username || '?')
      .split(/\s+/)
      .map((part) => part.charAt(0))
      .slice(0, 2)
      .join('')
      .toUpperCase();
  }

  private finishSignOut(): void {
    this.signingOut = false;
    this.changeDetector.markForCheck();
    void this.router.navigate(['/']);
  }
}
