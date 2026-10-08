import { CreateSoloSessionRequest, SessionConnectionInstructions } from './game-session.model';

/** Which devices play the round audio: every player's, or only the host's (same room). */
export type AudioMode = 'EVERYONE' | 'HOST_ONLY';

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
  playlistId: string;
  playlistName: string;
  trackCount: number;
  maxRoundSeconds: number;
  /** Requested star levels; `null` includes every level. */
  difficultyLevels: number[] | null;
  includeUnrated: boolean;
  audioMode: AudioMode;
}

/** Lobby creation or settings change: the solo configuration plus where the audio plays. */
export interface CreateLobbyRequest extends CreateSoloSessionRequest {
  audioMode: AudioMode;
}

/**
 * The lobby a player returns to after a multiplayer game (ADR-0023); guests get a new token
 * for the reopened lobby, registered players `null`.
 */
export interface RematchResponse {
  guestToken: string | null;
  lobby: Lobby;
}

/** A guest's participant token and the lobby it joined (ADR-0020). */
export interface GuestJoinResponse {
  guestToken: string;
  lobby: Lobby;
}

/** Nickname length rules shared with the server. */
export const GUEST_NICKNAME_MIN_LENGTH = 2;
export const GUEST_NICKNAME_MAX_LENGTH = 20;

/** Characters a join code may contain; 0, O, 1, and I are left out to avoid confusion. */
export const LOBBY_CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
export const LOBBY_CODE_LENGTH = 6;
