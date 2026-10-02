import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminInvitation } from './admin-invitation.model';
import { AdminInvitationService } from './admin-invitation.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-invitation-manager',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './invitation-manager.component.html',
  styleUrl: './invitation-manager.component.scss',
})
export class InvitationManagerComponent implements OnInit {
  private readonly invitationService = inject(AdminInvitationService);

  readonly emailControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.email, Validators.maxLength(255)],
  });
  readonly inviteForm = new FormGroup({ email: this.emailControl });
  invitations: AdminInvitation[] = [];
  loading = true;
  sending = false;
  busyId: string | null = null;
  listError = '';
  actionError = '';
  successMessage = '';

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.listError = '';
    this.invitationService.list().subscribe({
      next: (invitations) => {
        this.invitations = invitations;
        this.loading = false;
      },
      error: () => {
        this.listError = 'Invitations could not be loaded.';
        this.loading = false;
      },
    });
  }

  invite(): void {
    if (this.sending) {
      return;
    }
    if (this.emailControl.invalid) {
      this.emailControl.markAsTouched();
      return;
    }
    const email = this.emailControl.value.trim();
    this.sending = true;
    this.clearMessages();
    this.invitationService.invite(email).subscribe({
      next: (invitation) => {
        this.sending = false;
        this.emailControl.reset();
        this.successMessage = `Invitation sent to ${invitation.email}.`;
        this.refresh();
      },
      error: (error: HttpErrorResponse) => {
        this.sending = false;
        this.actionError = this.describeError(error);
      },
    });
  }

  resend(invitation: AdminInvitation): void {
    this.runRowAction(invitation, this.invitationService.resend(invitation.id), () => {
      this.successMessage = `A new invitation link was sent to ${invitation.email}.`;
    });
  }

  revoke(invitation: AdminInvitation): void {
    this.runRowAction(invitation, this.invitationService.revoke(invitation.id), () => {
      this.successMessage = `The invitation for ${invitation.email} was revoked.`;
    });
  }

  private runRowAction(
    invitation: AdminInvitation,
    action: Observable<unknown>,
    onSuccess: () => void,
  ): void {
    if (this.busyId) {
      return;
    }
    this.busyId = invitation.id;
    this.clearMessages();
    action.subscribe({
      next: () => {
        this.busyId = null;
        onSuccess();
        this.refresh();
      },
      error: (error: HttpErrorResponse) => {
        this.busyId = null;
        this.actionError = this.describeError(error);
        this.refresh();
      },
    });
  }

  private clearMessages(): void {
    this.successMessage = '';
    this.actionError = '';
  }

  private describeError(error: HttpErrorResponse): string {
    const code: string = error.error?.code ?? '';
    if (code === 'EMAIL_TAKEN') {
      return 'An account already uses this email.';
    }
    if (code === 'INVITATION_ALREADY_ACCEPTED') {
      return 'This invitation was already accepted.';
    }
    if (error.status === 400) {
      return 'Enter a valid email address.';
    }
    return 'The invitation could not be updated. Please try again.';
  }
}
