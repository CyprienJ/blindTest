import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-notification-toast',
  standalone: true,
  imports: [CommonModule],
  template: '<p>NotificationToast works</p>',
})
export class NotificationToastComponent {}
