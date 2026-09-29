import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-game-history',
  standalone: true,
  imports: [CommonModule],
  template: '<p>GameHistory works</p>',
})
export class GameHistoryComponent {}
