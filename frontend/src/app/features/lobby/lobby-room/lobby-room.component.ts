import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-lobby-room',
  standalone: true,
  imports: [CommonModule],
  template: '<p>LobbyRoom works</p>',
})
export class LobbyRoomComponent {}
