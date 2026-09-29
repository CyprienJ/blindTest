import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-friend-requests',
  standalone: true,
  imports: [CommonModule],
  template: '<p>FriendRequests works</p>',
})
export class FriendRequestsComponent {}
