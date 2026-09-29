import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-scoreboard',
  standalone: true,
  imports: [CommonModule],
  template: '<p>Scoreboard works</p>',
})
export class ScoreboardComponent {}
