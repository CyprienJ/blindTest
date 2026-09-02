import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { GameSessionService } from '../../../core/services/game-session.service';
import { GameSocketService } from '../../../core/services/game-socket.service';
import { ActiveSoloSession } from '../../../core/models/game-session.model';

@Component({
  selector: 'app-game-room',
  standalone: true,
  imports: [AsyncPipe, RouterLink],
  templateUrl: './game-room.component.html',
  styleUrl: './game-room.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameRoomComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gameSessions = inject(GameSessionService);
  private readonly gameSocket = inject(GameSocketService);

  session: ActiveSoloSession | null = null;
  readonly status$ = this.gameSocket.status$;

  ngOnInit(): void {
    const sessionId = this.route.snapshot.paramMap.get('sessionId');
    this.session = sessionId ? this.gameSessions.getActiveSession(sessionId) : null;
    if (!this.session) {
      void this.router.navigate(['/lobby/create']);
      return;
    }
    this.gameSocket.connect(this.session);
  }

  ngOnDestroy(): void {
    this.gameSocket.disconnect();
  }
}
