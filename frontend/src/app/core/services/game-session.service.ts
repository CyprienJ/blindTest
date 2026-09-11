import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../config';
import { GameSummary } from '../models/game-summary.model';
import {
  ActiveSoloSession,
  CreateSoloSessionRequest,
  SoloSessionResponse,
} from '../models/game-session.model';

const ACTIVE_SOLO_SESSION_KEY = 'active_solo_session';

@Injectable({ providedIn: 'root' })
export class GameSessionService {
  private readonly http = inject(HttpClient);
  private activeSession: ActiveSoloSession | null = readStoredSession();

  createSolo(request: CreateSoloSessionRequest): Observable<SoloSessionResponse> {
    return this.http.post<SoloSessionResponse>(`${API_BASE}/sessions`, request);
  }

  /** Fetches the durable, refresh-safe completed-game summary for its owner. */
  getSummary(sessionId: string): Observable<GameSummary> {
    return this.http.get<GameSummary>(`${API_BASE}/sessions/${sessionId}/summary`);
  }

  setActiveSession(session: ActiveSoloSession): void {
    this.activeSession = session;
    try {
      sessionStorage.setItem(ACTIVE_SOLO_SESSION_KEY, JSON.stringify(session));
    } catch {
      // Storage may be unavailable (private mode); the in-memory copy still works.
    }
  }

  getActiveSession(sessionId: string): ActiveSoloSession | null {
    const session = this.activeSession ?? readStoredSession();
    if (session && session.sessionId === sessionId) {
      this.activeSession = session;
      return session;
    }
    return null;
  }

  clearActiveSession(): void {
    this.activeSession = null;
    try {
      sessionStorage.removeItem(ACTIVE_SOLO_SESSION_KEY);
    } catch {
      // Ignore storage errors.
    }
  }
}

function readStoredSession(): ActiveSoloSession | null {
  try {
    const raw = sessionStorage.getItem(ACTIVE_SOLO_SESSION_KEY);
    return raw ? (JSON.parse(raw) as ActiveSoloSession) : null;
  } catch {
    return null;
  }
}
