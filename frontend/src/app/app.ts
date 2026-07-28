import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  imports: [AsyncPipe, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly currentUser$ = this.authService.currentUser$;
  signingOut = false;

  signOut(): void {
    this.signingOut = true;
    this.authService.logout().subscribe({
      next: () => this.finishSignOut(),
      error: () => this.finishSignOut(),
    });
  }

  private finishSignOut(): void {
    this.signingOut = false;
    void this.router.navigate(['/auth/login']);
  }
}
