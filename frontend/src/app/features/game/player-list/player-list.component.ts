import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-player-list',
  standalone: true,
  imports: [CommonModule],
  template: '<p>PlayerList works</p>',
})
export class PlayerListComponent {}
