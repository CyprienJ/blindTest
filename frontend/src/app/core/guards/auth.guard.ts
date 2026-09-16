import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  return authService.refreshToken().pipe(
    map(() => {
      if (authService.isLoggedIn()) return true;
      authService.clearSession();
      return router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
    }),
    catchError(() => {
      authService.clearSession();
      return of(router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } }));
    }),
  );
};
