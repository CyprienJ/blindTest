import { Injectable } from '@angular/core';

const GUEST_SESSION_KEY = 'guest_session';

/** What a guest tab remembers about the one game it joined (ADR-0020). */
export interface GuestSession {
  token: string;
  code: string;
  sessionId: string;
  participantId: string;
  nickname: string;
}

/**
 * Keeps the guest participant token in `sessionStorage`, so reloading the tab rejoins the game
 * while a new or closed tab cannot. Guests have no account and no history.
 */
@Injectable({ providedIn: 'root' })
export class GuestSessionService {
  private session: GuestSession | null = readStoredSession();

  get(): GuestSession | null {
    return this.session;
  }

  token(): string | null {
    return this.session?.token ?? null;
  }

  set(session: GuestSession): void {
    this.session = session;
    try {
      sessionStorage.setItem(GUEST_SESSION_KEY, JSON.stringify(session));
    } catch {
      // Storage may be unavailable (private mode); the in-memory copy still works.
    }
  }

  clear(): void {
    this.session = null;
    try {
      sessionStorage.removeItem(GUEST_SESSION_KEY);
    } catch {
      // Ignore storage errors.
    }
  }

  /** Whether the guest may open this URL: its own lobby, game room, or end screen. */
  allows(url: string): boolean {
    const session = this.session;
    if (!session) {
      return false;
    }
    const path = url.split(/[?#]/, 1)[0];
    return (
      path === `/lobby/${session.code}` ||
      path === `/game/${session.sessionId}` ||
      path === `/end-screen/${session.sessionId}`
    );
  }
}

function readStoredSession(): GuestSession | null {
  try {
    const raw = sessionStorage.getItem(GUEST_SESSION_KEY);
    return raw ? (JSON.parse(raw) as GuestSession) : null;
  } catch {
    return null;
  }
}
