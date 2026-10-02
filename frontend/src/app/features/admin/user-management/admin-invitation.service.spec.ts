import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminInvitationService } from './admin-invitation.service';

describe('AdminInvitationService', () => {
  let service: AdminInvitationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminInvitationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists, creates, resends, and revokes invitations through the administrator API', () => {
    service.list().subscribe();
    expect(http.expectOne('/api/admin/invitations').request.method).toBe('GET');

    service.invite('friend@example.com').subscribe();
    const create = http.expectOne('/api/admin/invitations');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ email: 'friend@example.com' });

    service.resend('invitation-id').subscribe();
    expect(http.expectOne('/api/admin/invitations/invitation-id/resend').request.method).toBe(
      'POST',
    );

    service.revoke('invitation-id').subscribe();
    expect(http.expectOne('/api/admin/invitations/invitation-id').request.method).toBe('DELETE');
  });
});
