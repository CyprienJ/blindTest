import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-join-session',
  standalone: true,
  imports: [CommonModule],
  template: '<p>JoinSession works</p>',
})
export class JoinSessionComponent {}
