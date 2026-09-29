import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-friend-list',
  standalone: true,
  imports: [CommonModule],
  template: '<p>FriendList works</p>',
})
export class FriendListComponent {}
