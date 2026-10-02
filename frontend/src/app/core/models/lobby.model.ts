import { SessionConnectionInstructions } from './game-session.model';

/** Public lobby member; never carries an account identity. */
export interface LobbyParticipant {
  participantId: string;
  displayName: string;
  ready: boolean;
  host: boolean;
  guest: boolean;
}

/** Lobby state returned to one member by the `/api/lobbies` endpoints (MP-02). */
export interface Lobby {
  sessionId: string;
  code: string;
  /** The reading member's own participant identity. */
  participantId: string;
  hostParticipantId: string;
  participants: LobbyParticipant[];
  canStart: boolean;
  maxParticipants: number;
  version: number;
  expiresAt: string;
  connection: SessionConnectionInstructions;
  playlistName: string;
  trackCount: number;
  maxRoundSeconds: number;
}

/** Characters a join code may contain; 0, O, 1, and I are left out to avoid confusion. */
export const LOBBY_CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
export const LOBBY_CODE_LENGTH = 6;
