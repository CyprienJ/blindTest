import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminUserService } from './admin-user.service';

describe('AdminUserService', () => {
  let service: AdminUserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminUserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists, edits, and deletes accounts through the administrator API', () => {
    service.list().subscribe();
    expect(http.expectOne('/api/admin/users').request.method).toBe('GET');

    service.update('user-id', { username: 'name', email: 'a@b.c', role: 'ADMIN' }).subscribe();
    const update = http.expectOne('/api/admin/users/user-id');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ username: 'name', email: 'a@b.c', role: 'ADMIN' });

    service.delete('user-id').subscribe();
    expect(http.expectOne('/api/admin/users/user-id').request.method).toBe('DELETE');
  });
});
