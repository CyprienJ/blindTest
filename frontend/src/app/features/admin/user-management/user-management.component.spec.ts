import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AdminInvitation } from './admin-invitation.model';
import { AdminInvitationService } from './admin-invitation.service';
import { UserManagementComponent } from './user-management.component';

const PENDING: AdminInvitation = {
  id: 'pending-id',
  email: 'pending@example.com',
  status: 'PENDING',
  invitedBy: 'admin',
  createdAt: '2026-10-02T08:00:00Z',
  expiresAt: '2026-10-04T08:00:00Z',
  acceptedAt: null,
  revokedAt: null,
};

const ACCEPTED: AdminInvitation = {
  ...PENDING,
  id: 'accepted-id',
  email: 'accepted@example.com',
  status: 'ACCEPTED',
  acceptedAt: '2026-10-02T09:00:00Z',
};

describe('UserManagementComponent', () => {
  let service: jasmine.SpyObj<AdminInvitationService>;
  let fixture: ComponentFixture<UserManagementComponent>;

  beforeEach(async () => {
    service = jasmine.createSpyObj<AdminInvitationService>('AdminInvitationService', [
      'list',
      'invite',
      'resend',
      'revoke',
    ]);
    service.list.and.returnValue(of([PENDING, ACCEPTED]));

    await TestBed.configureTestingModule({
      imports: [UserManagementComponent],
      providers: [{ provide: AdminInvitationService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
  });

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function button(label: string): HTMLButtonElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`button[aria-label="${label}"]`);
  }

  it('lists invitations and only offers actions that apply to each status', () => {
    expect(text()).toContain('pending@example.com');
    expect(text()).toContain('accepted@example.com');
    expect(button('Revoke invitation to pending@example.com')).not.toBeNull();
    expect(button('Resend invitation to pending@example.com')).not.toBeNull();
    expect(button('Revoke invitation to accepted@example.com')).toBeNull();
    expect(button('Resend invitation to accepted@example.com')).toBeNull();
  });

  it('sends an invitation and refreshes the list', () => {
    service.invite.and.returnValue(of({ ...PENDING, email: 'new@example.com' }));
    const component = fixture.componentInstance;
    component.emailControl.setValue('new@example.com');

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('button[type="submit"]')!
      .click();
    fixture.detectChanges();

    expect(service.invite).toHaveBeenCalledOnceWith('new@example.com');
    expect(text()).toContain('Invitation sent to new@example.com.');
    expect(component.emailControl.value).toBe('');
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('does not send an invalid address', () => {
    const component = fixture.componentInstance;
    component.emailControl.setValue('not-an-email');

    component.invite();
    fixture.detectChanges();

    expect(service.invite).not.toHaveBeenCalled();
    expect(text()).toContain('Enter a valid email address');
  });

  it('explains that an address already has an account', () => {
    service.invite.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'EMAIL_TAKEN' } })),
    );
    const component = fixture.componentInstance;
    component.emailControl.setValue('taken@example.com');

    component.invite();
    fixture.detectChanges();

    expect(text()).toContain('An account already uses this email.');
  });

  it('revokes and resends from the row actions', () => {
    service.revoke.and.returnValue(of(undefined));
    service.resend.and.returnValue(of(PENDING));

    button('Revoke invitation to pending@example.com')!.click();
    fixture.detectChanges();
    expect(service.revoke).toHaveBeenCalledOnceWith('pending-id');
    expect(text()).toContain('The invitation for pending@example.com was revoked.');

    button('Resend invitation to pending@example.com')!.click();
    fixture.detectChanges();
    expect(service.resend).toHaveBeenCalledOnceWith('pending-id');
    expect(text()).toContain('A new invitation link was sent to pending@example.com.');
  });
});
