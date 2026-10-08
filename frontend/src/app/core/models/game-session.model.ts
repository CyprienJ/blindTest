export interface SessionConnectionInstructions {
  protocolVersion: number;
  webSocketEndpoint: string;
  sockJsEndpoint: string;
  commandDestination: string;
  eventDestination: string;
  privateDestination: string;
}

export interface SoloSessionResponse {
  sessionId: string;
  participantId: string;
  state: string;
  version: number;
  expiresAt: string;
  connection: SessionConnectionInstructions;
}

export interface CreateSoloSessionRequest {
  playlistId: string;
  trackCount: number;
  maxRoundSeconds: number;
  extensions: Record<string, never>;
  /** Star levels (1 easiest to 5 hardest) to include. */
  difficultyLevels: number[];
  /** Whether tracks without a difficulty rating are included. */
  includeUnrated: boolean;
  /** Plays every matching track; the server then ignores `trackCount`. */
  allTracks: boolean;
}

/** Server response enriched with the display details the game room needs before SESSION_READY. */
export interface ActiveSoloSession extends SoloSessionResponse {
  playlistName: string;
  trackCount: number;
  maxRoundSeconds: number;
  /**
   * False for a multiplayer guest of the host: the game room then waits for the host's
   * START_GAME instead of sending it. Absent for solo games, whose player is the owner.
   */
  host?: boolean;
  /** The lobby host, for the multiplayer scoreboard's host marker. */
  hostParticipantId?: string;
  /** Multiplayer only: `HOST_ONLY` keeps every other player's browser silent. */
  audioMode?: 'EVERYONE' | 'HOST_ONLY';
}
