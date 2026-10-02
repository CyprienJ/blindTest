import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AccountListComponent } from './account-list.component';
import { InvitationManagerComponent } from './invitation-manager.component';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-user-management',
  standalone: true,
  imports: [AccountListComponent, InvitationManagerComponent],
  template: `
    <main class="user-management">
      <div class="page-heading">
        <h1 i18n>Users</h1>
        <p i18n>Manage accounts and invite new players. Registration is invitation-only.</p>
      </div>
      <app-account-list />
      <app-invitation-manager />
    </main>
  `,
  styles: `
    .user-management {
      display: grid;
      gap: 42px;
      width: 100%;
      max-width: 1100px;
      padding: 42px 40px 80px;
    }

    .page-heading h1 {
      margin: 0 0 7px;
      font-size: 27px;
      letter-spacing: -0.03em;
    }

    .page-heading p {
      margin: 0;
      color: var(--fg-2);
      font-size: 13px;
    }

    @media (max-width: 720px) {
      .user-management {
        padding: 28px 16px 60px;
      }
    }
  `,
})
export class UserManagementComponent {}
