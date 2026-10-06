import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { GuestSessionService } from '../services/guest-session.service';
import { Router } from '@angular/router';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const guestSession = inject(GuestSessionService);
  const router = inject(Router);

  const accountToken = authService.getAccessToken();
  // A guest tab has no account; its participant token only reaches its own lobby and game.
  const guestToken = accountToken ? null : guestSession.token();
  const token = accountToken ?? guestToken;
  const isAuthEndpoint = req.url.includes('/api/auth/');
  const authReq =
    token && !isAuthEndpoint
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && guestToken) {
        // Guest tokens cannot be refreshed; the guest has to join again.
        guestSession.clear();
        void router.navigate(['/lobby/join']);
        return throwError(() => err);
      }
      if (err.status === 401 && !req.url.includes('/auth/')) {
        return authService.refreshToken().pipe(
          switchMap((res) => {
            const retried = req.clone({
              setHeaders: { Authorization: `Bearer ${res.accessToken}` },
            });
            return next(retried);
          }),
          catchError((refreshErr) => {
            authService.clearSession();
            router.navigate(['/auth/login'], { queryParams: { returnUrl: router.url } });
            return throwError(() => refreshErr);
          }),
        );
      }
      return throwError(() => err);
    }),
  );
};
