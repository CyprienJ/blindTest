import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule],
  template: '<p>UserManagement works</p>',
})
export class UserManagementComponent {}
