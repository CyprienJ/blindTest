import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../config';
import { SoloSessionResponse } from '../models/game-session.model';
import { AudioMode, CreateLobbyRequest, GuestJoinResponse, Lobby } from '../models/lobby.model';

/** REST client for multiplayer lobbies; live changes arrive as STOMP lobby events. */
@Injectable({ providedIn: 'root' })
export class LobbyService {
  private readonly http = inject(HttpClient);

  create(request: CreateLobbyRequest): Observable<Lobby> {
    return this.http.post<Lobby>(`${API_BASE}/lobbies`, request);
  }

  get(code: string): Observable<Lobby> {
    return this.http.get<Lobby>(`${API_BASE}/lobbies/${encodeURIComponent(code)}`);
  }

  join(code: string): Observable<Lobby> {
    return this.http.post<Lobby>(
      `${API_BASE}/lobbies/${encodeURIComponent(code)}/participants`,
      {},
    );
  }

  /** Joins without an account; the returned token only reaches this lobby and its game. */
  joinAsGuest(
    code: string,
    nickname: string,
    turnstileToken: string,
  ): Observable<GuestJoinResponse> {
    return this.http.post<GuestJoinResponse>(
      `${API_BASE}/lobbies/${encodeURIComponent(code)}/guests`,
      { nickname, turnstileToken },
    );
  }

  setAudioMode(code: string, audioMode: AudioMode): Observable<Lobby> {
    return this.http.put<Lobby>(`${API_BASE}/lobbies/${encodeURIComponent(code)}/audio-mode`, {
      audioMode,
    });
  }

  leave(code: string): Observable<void> {
    return this.http.delete<void>(
      `${API_BASE}/lobbies/${encodeURIComponent(code)}/participants/me`,
    );
  }

  setReady(code: string, ready: boolean): Observable<Lobby> {
    return this.http.put<Lobby>(
      `${API_BASE}/lobbies/${encodeURIComponent(code)}/participants/me/ready`,
      { ready },
    );
  }

  kick(code: string, participantId: string): Observable<void> {
    return this.http.delete<void>(
      `${API_BASE}/lobbies/${encodeURIComponent(code)}/participants/${participantId}`,
    );
  }

  start(code: string): Observable<SoloSessionResponse> {
    return this.http.post<SoloSessionResponse>(
      `${API_BASE}/lobbies/${encodeURIComponent(code)}/start`,
      {},
    );
  }
}
