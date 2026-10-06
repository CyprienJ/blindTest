import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { GuestSessionService } from '../services/guest-session.service';

/** Screens open without an account: the join screen, where guests pick a nickname. */
function isGuestEntry(url: string): boolean {
  return url.split(/[?#]/, 1)[0] === '/lobby/join';
}

/**
 * Admits signed-in accounts everywhere, a guest tab to its own lobby, game, and end screen
 * (ADR-0020), and anyone to the join screen once a silent refresh shows there is no account.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const guestSession = inject(GuestSessionService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }
  if (guestSession.allows(state.url)) {
    return true;
  }

  const fallback = () => {
    authService.clearSession();
    return isGuestEntry(state.url)
      ? true
      : router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
  };

  return authService.refreshToken().pipe(
    map(() => (authService.isLoggedIn() ? true : fallback())),
    catchError(() => of(fallback())),
  );
};
