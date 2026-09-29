import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE } from '../config';
import { RefreshResponse } from '../models/user.model';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('shares a single refresh request between concurrent callers', () => {
    const received: RefreshResponse[] = [];
    service.refreshToken().subscribe((res) => received.push(res));
    service.refreshToken().subscribe((res) => received.push(res));
    service.refreshToken().subscribe((res) => received.push(res));

    const req = http.expectOne(`${API_BASE}/auth/refresh`);
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ accessToken: 'new-token' });

    expect(received.map((r) => r.accessToken)).toEqual(['new-token', 'new-token', 'new-token']);
    expect(service.getAccessToken()).toBe('new-token');
  });

  it('shares a failed refresh between concurrent callers', () => {
    const errors: number[] = [];
    service.refreshToken().subscribe({ error: (err) => errors.push(err.status) });
    service.refreshToken().subscribe({ error: (err) => errors.push(err.status) });

    http
      .expectOne(`${API_BASE}/auth/refresh`)
      .flush({ code: 'INVALID_REFRESH_TOKEN' }, { status: 401, statusText: 'Unauthorized' });

    expect(errors).toEqual([401, 401]);
  });

  it('sends a new request once the previous refresh has completed', () => {
    service.refreshToken().subscribe();
    http.expectOne(`${API_BASE}/auth/refresh`).flush({ accessToken: 'first' });

    service.refreshToken().subscribe();
    http.expectOne(`${API_BASE}/auth/refresh`).flush({ accessToken: 'second' });

    expect(service.getAccessToken()).toBe('second');
  });
});
