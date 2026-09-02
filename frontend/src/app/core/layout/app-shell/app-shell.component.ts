import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [AsyncPipe, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly currentUser$ = this.authService.currentUser$;
  readonly isAdmin = this.authService.isAdmin();
  signingOut = false;

  signOut(): void {
    this.signingOut = true;
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
    void this.router.navigate(['/']);
  }
}
