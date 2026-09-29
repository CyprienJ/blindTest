import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-my-profile',
  standalone: true,
  imports: [CommonModule],
  template: '<p>MyProfile works</p>',
})
export class MyProfileComponent {}
