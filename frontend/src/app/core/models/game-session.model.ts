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
}

/** Server response enriched with the display details the game room needs before SESSION_READY. */
export interface ActiveSoloSession extends SoloSessionResponse {
  playlistName: string;
  trackCount: number;
  maxRoundSeconds: number;
}
