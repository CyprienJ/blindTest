import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  inject,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { filter, finalize, Observable } from 'rxjs';
import { LobbyUpdatedPayload } from '../../../core/models/game-protocol.model';
import { AudioMode, Lobby, LobbyParticipant } from '../../../core/models/lobby.model';
import { GameSessionService } from '../../../core/services/game-session.service';
import { GameSocketService } from '../../../core/services/game-socket.service';
import { GuestSessionService } from '../../../core/services/guest-session.service';
import { LobbyService } from '../../../core/services/lobby.service';

type LobbyState = 'loading' | 'open' | 'removed' | 'unavailable';

/**
 * Multiplayer lobby (MP-05): membership comes from REST and stays live through the session's
 * public STOMP topic until the host starts, when every member moves to the game room.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-lobby-room',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './lobby-room.component.html',
  styleUrl: './lobby-room.component.scss',
})
export class LobbyRoomComponent implements OnInit, OnDestroy {
  private readonly lobbies = inject(LobbyService);
  private readonly gameSessions = inject(GameSessionService);
  private readonly gameSocket = inject(GameSocketService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly guestSession = inject(GuestSessionService);

  readonly hostLabel = $localize`Host`;
  readonly readyLabel = $localize`Ready`;
  readonly notReadyLabel = $localize`Not ready`;

  state: LobbyState = 'loading';
  lobby: Lobby | null = null;
  busy = false;
  errorMessage = '';
  copied = false;
  private code = '';
  private entering = false;
  private socketOpened = false;

  ngOnInit(): void {
    this.code = (this.route.snapshot.paramMap.get('code') ?? '').toUpperCase();
    this.lobbies.get(this.code).subscribe({
      next: (lobby) => {
        this.lobby = lobby;
        this.state = 'open';
        this.listen(lobby);
      },
      error: (error) => {
        this.state = 'unavailable';
        this.errorMessage = this.describe(error);
      },
    });
  }

  ngOnDestroy(): void {
    this.gameSocket.disconnect();
  }

  get me(): LobbyParticipant | undefined {
    return this.lobby?.participants.find(
      (participant) => participant.participantId === this.lobby?.participantId,
    );
  }

  get isHost(): boolean {
    return !!this.lobby && this.lobby.hostParticipantId === this.lobby.participantId;
  }

  /** Guests have no account home; they go back to the public page. */
  get homeLink(): string {
    return this.guestSession.get() ? '/' : '/home';
  }

  get readyCount(): number {
    return (
      this.lobby?.participants.filter((participant) => participant.ready || participant.host)
        .length ?? 0
    );
  }

  get startHint(): string {
    if (!this.lobby || this.lobby.canStart) {
      return '';
    }
    return this.lobby.participants.length < 2
      ? $localize`Waiting for at least one more player.`
      : $localize`Waiting for every player to be ready.`;
  }

  removeLabel(name: string): string {
    return $localize`Remove ${name}:name:`;
  }

  initials(name: string): string {
    return name.slice(0, 2).toUpperCase();
  }

  copyCode(): void {
    if (!this.lobby) {
      return;
    }
    const link = `${window.location.origin}/lobby/join?code=${this.lobby.code}`;
    void navigator.clipboard?.writeText(`${this.lobby.code} — ${link}`).then(() => {
      this.copied = true;
      this.changeDetector.markForCheck();
      setTimeout(() => {
        this.copied = false;
        this.changeDetector.markForCheck();
      }, 1500);
    });
  }

  toggleReady(): void {
    const me = this.me;
    if (!me || this.busy) {
      return;
    }
    this.run(this.lobbies.setReady(this.code, !me.ready), (lobby) => (this.lobby = lobby));
  }

  kick(participant: LobbyParticipant): void {
    this.run(this.lobbies.kick(this.code, participant.participantId), () => {
      if (this.lobby) {
        this.lobby = {
          ...this.lobby,
          participants: this.lobby.participants.filter(
            (member) => member.participantId !== participant.participantId,
          ),
        };
      }
    });
  }

  setAudioMode(audioMode: AudioMode): void {
    if (!this.lobby || this.lobby.audioMode === audioMode) {
      return;
    }
    this.run(this.lobbies.setAudioMode(this.code, audioMode), (lobby) => (this.lobby = lobby));
  }

  start(): void {
    this.run(this.lobbies.start(this.code), () => this.enterGame());
  }

  leave(): void {
    this.run(this.lobbies.leave(this.code), () => {
      this.gameSocket.disconnect();
      const home = this.homeLink;
      this.guestSession.clear();
      void this.router.navigate([home]);
    });
  }

  private listen(lobby: Lobby): void {
    this.gameSocket.connect({
      sessionId: lobby.sessionId,
      participantId: lobby.participantId,
      state: 'LOBBY',
      version: lobby.version,
      expiresAt: lobby.expiresAt,
      connection: lobby.connection,
      playlistName: lobby.playlistName,
      trackCount: lobby.trackCount,
      maxRoundSeconds: lobby.maxRoundSeconds,
    });
    this.gameSocket.status$
      .pipe(
        filter((status) => status === 'connected'),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        // Updates between the join response and the subscription are not replayed.
        if (!this.socketOpened) {
          this.socketOpened = true;
          this.refresh();
        }
      });
    this.gameSocket.publicEvents$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((event) => {
      if (event.type === 'LOBBY_UPDATED') {
        this.apply(event.payload);
      } else if (event.type === 'LOBBY_STARTED') {
        this.enterGame(event.payload.totalRounds);
      }
      this.changeDetector.markForCheck();
    });
  }

  private refresh(): void {
    this.lobbies.get(this.code).subscribe({
      next: (lobby) => (this.lobby = lobby),
      error: (error) => {
        if (error instanceof HttpErrorResponse && error.status === 404 && this.state === 'open') {
          this.state = 'unavailable';
          this.errorMessage = this.describe(error);
        }
      },
    });
  }

  private apply(update: LobbyUpdatedPayload): void {
    if (!this.lobby) {
      return;
    }
    if (!update.participants.some((member) => member.participantId === this.lobby?.participantId)) {
      this.state = 'removed';
      this.gameSocket.disconnect();
      return;
    }
    this.lobby = {
      ...this.lobby,
      hostParticipantId: update.hostParticipantId,
      participants: update.participants,
      canStart: update.canStart,
      audioMode: update.audioMode,
      playlistName: update.playlistName,
      trackCount: update.trackCount,
      maxRoundSeconds: update.maxRoundSeconds,
      allTracks: update.allTracks,
    };
  }

  /** With "all tracks", the started game's size can differ from the lobby's last count. */
  private enterGame(totalRounds?: number): void {
    if (this.entering || !this.lobby) {
      return;
    }
    this.entering = true;
    const lobby = this.lobby;
    this.gameSessions.setActiveSession({
      sessionId: lobby.sessionId,
      participantId: lobby.participantId,
      state: 'CREATED',
      version: 0,
      expiresAt: lobby.expiresAt,
      connection: lobby.connection,
      playlistName: lobby.playlistName,
      trackCount: totalRounds ?? lobby.trackCount,
      maxRoundSeconds: lobby.maxRoundSeconds,
      host: this.isHost,
      hostParticipantId: lobby.hostParticipantId,
      audioMode: lobby.audioMode,
    });
    void this.router.navigate(['/game', lobby.sessionId]);
  }

  private run<T>(request: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy = true;
    this.errorMessage = '';
    request.pipe(finalize(() => (this.busy = false))).subscribe({
      next: onSuccess,
      error: (error) => {
        this.errorMessage = this.describe(error);
        if (error instanceof HttpErrorResponse && error.error?.code === 'LOBBY_NOT_FOUND') {
          this.state = 'unavailable';
        }
      },
    });
  }

  private describe(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.error?.code) {
        case 'LOBBY_NOT_FOUND':
          return $localize`This lobby is no longer open. It may have started, expired, or closed.`;
        case 'LOBBY_HOST_REQUIRED':
          return $localize`Only the host can do this.`;
        case 'LOBBY_NOT_READY':
          return $localize`Every player must be ready, and at least two players must be present.`;
        case 'LOBBY_PARTICIPANT_NOT_FOUND':
          return $localize`That player has already left the lobby.`;
        case 'GAME_PLAYLIST_INSUFFICIENT_TRACKS':
        case 'GAME_DIFFICULTY_INSUFFICIENT_TRACKS':
        case 'GAME_PLAYLIST_INELIGIBLE':
        case 'GAME_PLAYLIST_NOT_FOUND':
          return $localize`The playlist no longer has enough ready tracks. Change the settings to pick another playlist or fewer tracks.`;
        default:
          break;
      }
      if (error.status === 0) {
        return $localize`The server can’t be reached. Check your connection and try again.`;
      }
    }
    return $localize`Something went wrong. Please try again.`;
  }
}
