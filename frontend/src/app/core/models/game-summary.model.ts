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
}

/** Durable, refresh-safe owner projection returned by GET /sessions/{sessionId}/summary. */
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
}
