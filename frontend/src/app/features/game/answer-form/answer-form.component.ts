import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-answer-form',
  standalone: true,
  imports: [CommonModule],
  template: '<p>AnswerForm works</p>',
})
export class AnswerFormComponent {}
