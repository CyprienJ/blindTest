import { RoundOutcome } from './game-protocol.model';

export interface GameSummaryRound {
  roundIndex: number;
  outcome: RoundOutcome;
  points: number;
  title: string;
  artist: string;
  composer: string | null;
  album: string | null;
  startedAt: string;
  revealedAt: string;
  completedAt: string;
  /** Whether the reading participant found this title. */
  titleFound: boolean;
}

export interface GameSummaryStanding {
  participantId: string;
  displayName: string;
  finalScore: number;
  rank: number;
  guest: boolean;
}

/**
 * Durable, refresh-safe projection returned by GET /sessions/{sessionId}/summary to a
 * participant. Scores, titles found, and round points are the reader's own.
 */
export interface GameSummary {
  sessionId: string;
  totalScore: number;
  totalRounds: number;
  titlesFound: number;
  skippedRounds: number;
  timedOutRounds: number;
  startedAt: string;
  completedAt: string;
  rounds: GameSummaryRound[];
  gameMode: 'SOLO' | 'MULTIPLAYER';
  participantId: string;
  standings: GameSummaryStanding[];
}
