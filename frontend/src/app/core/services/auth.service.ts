import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, finalize, Observable, shareReplay, tap } from 'rxjs';
import { API_BASE } from '../config';
import {
  LoginResponse,
  RefreshResponse,
  RegisterResponse,
  UserSummary,
} from '../models/user.model';

const ACCESS_TOKEN_KEY = 'access_token';
const CURRENT_USER_KEY = 'current_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly currentUserSubject: BehaviorSubject<UserSummary | null>;
  readonly currentUser$: Observable<UserSummary | null>;
  private refreshInFlight: Observable<RefreshResponse> | null = null;

  constructor() {
    const stored = localStorage.getItem(CURRENT_USER_KEY);
    this.currentUserSubject = new BehaviorSubject<UserSummary | null>(
      stored ? (JSON.parse(stored) as UserSummary) : null,
    );
    this.currentUser$ = this.currentUserSubject.asObservable();
  }

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return this.currentUserSubject.value !== null && this.getAccessToken() !== null;
  }

  isAdmin(): boolean {
    return this.isLoggedIn() && this.currentUserSubject.value?.role === 'ADMIN';
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${API_BASE}/auth/login`, { email, password })
      .pipe(tap((res) => this.storeSession(res.accessToken, res.user)));
  }

  register(
    username: string,
    email: string,
    password: string,
    turnstileToken: string,
  ): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${API_BASE}/auth/register`, {
      username,
      email,
      password,
      turnstileToken,
    });
  }

  verifyEmail(token: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/auth/verify-email`, { token });
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/auth/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/auth/reset-password`, { token, newPassword });
  }

  /**
   * Rotates the refresh cookie. Concurrent callers (e.g. several requests hitting 401 at once)
   * share a single in-flight request, since the server invalidates the cookie on first use.
   */
  refreshToken(): Observable<RefreshResponse> {
    this.refreshInFlight ??= this.http
      .post<RefreshResponse>(`${API_BASE}/auth/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((res) => localStorage.setItem(ACCESS_TOKEN_KEY, res.accessToken)),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    return this.refreshInFlight;
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${API_BASE}/auth/logout`, {}, { withCredentials: true })
      .pipe(finalize(() => this.clearSession()));
  }

  clearSession(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(CURRENT_USER_KEY);
    this.currentUserSubject.next(null);
  }

  private storeSession(accessToken: string, user: UserSummary): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(user));
    this.currentUserSubject.next(user);
  }
}
