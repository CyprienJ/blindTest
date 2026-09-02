import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { GameSessionService } from '../../../core/services/game-session.service';
import { ActiveSoloSession } from '../../../core/models/game-session.model';

@Component({
  selector: 'app-game-room',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './game-room.component.html',
  styleUrl: './game-room.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameRoomComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gameSessions = inject(GameSessionService);

  session: ActiveSoloSession | null = null;

  ngOnInit(): void {
    const sessionId = this.route.snapshot.paramMap.get('sessionId');
    this.session = sessionId ? this.gameSessions.getActiveSession(sessionId) : null;
    if (!this.session) {
      void this.router.navigate(['/lobby/create']);
    }
  }
}
