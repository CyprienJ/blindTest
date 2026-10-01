/**
 * TypeScript mirror of the gameplay STOMP protocol (V1) defined by the backend
 * `com.blindtest.websocket.protocol` package and `docs/websocket-protocol.md`.
 * Absent Jackson `Optional` fields arrive as `null`.
 */

export const GAME_PROTOCOL_VERSION = 1;

export type GameStatus = 'CREATED' | 'PLAYING' | 'FINISHED' | 'ABORTED';
export type RoundStatus = 'PREPARING' | 'PLAYING' | 'REVEALED' | 'COMPLETED';
export type RoundOutcome = 'TITLE_FOUND' | 'TIMEOUT' | 'SKIPPED';
export type GameAnswerType = 'TITLE' | 'ARTIST' | 'COMPOSER' | 'ALBUM';
export type SecondaryAnswerType = 'ARTIST' | 'COMPOSER' | 'ALBUM';

export type AnswerRejectionReason =
  | 'WRONG_ANSWER'
  | 'ALREADY_FOUND'
  | 'COOLDOWN'
  | 'STALE_SESSION_VERSION'
  | 'INVALID_ROUND_STATE'
  | 'DUPLICATE_COMMAND';

export type SessionCommandType =
  | 'START_GAME'
  | 'SUBMIT_ANSWER'
  | 'SKIP_ROUND'
  | 'ADVANCE_ROUND'
  | 'REQUEST_SNAPSHOT';

export interface SessionCommand {
  protocolVersion: number;
  commandId: string;
  expectedSessionVersion: number;
  type: SessionCommandType;
  answer?: string;
}

export interface SessionEventMetadata {
  protocolVersion: number;
  eventId: string;
  sessionId: string;
  sessionVersion: number;
  occurredAt: string;
}

/* ── Shared (topic) events ─────────────────────────────────────────────── */

export interface SessionReadyPayload {
  state: GameStatus;
  totalRounds: number;
  expiresAt: string;
}

export interface RoundPreparingPayload {
  roundIndex: number;
  totalRounds: number;
}

export interface RoundStartedPayload {
  roundIndex: number;
  totalRounds: number;
  startsAt: string;
  deadline: string;
  excerptStartSeconds: number;
  /** Excerpt window length; playback loops it until the round ends. */
  excerptDurationSeconds: number;
  playbackUrl: string | null;
}

/** Available listening links for a revealed track, keyed by platform. */
export type RevealStreamingLinks = Partial<
  Record<'deezer' | 'youtube' | 'spotify' | 'qobuz' | 'tidal' | 'soundcloud', string>
>;

export interface RoundRevealedPayload {
  roundIndex: number;
  totalRounds: number;
  outcome: RoundOutcome;
  title: string;
  artist: string;
  composer: string | null;
  album: string | null;
  streamingLinks: RevealStreamingLinks;
  /** Same-origin path of the track cover, when one is stored. */
  coverUrl: string | null;
  roundPoints: number;
  totalScore: number;
  transitionAt: string;
}

export interface ScoreUpdatedPayload {
  participantId: string;
  roundPoints: number;
  totalScore: number;
}

export interface GameFinishedPayload {
  totalScore: number;
  totalRounds: number;
  titlesFound: number;
  skippedRounds: number;
  timedOutRounds: number;
}

export type PublicSessionEvent =
  | PublicEvent<'SESSION_READY', SessionReadyPayload>
  | PublicEvent<'ROUND_PREPARING', RoundPreparingPayload>
  | PublicEvent<'ROUND_STARTED', RoundStartedPayload>
  | PublicEvent<'ROUND_REVEALED', RoundRevealedPayload>
  | PublicEvent<'SCORE_UPDATED', ScoreUpdatedPayload>
  | PublicEvent<'GAME_FINISHED', GameFinishedPayload>;

interface PublicEvent<T extends string, P> {
  metadata: SessionEventMetadata;
  type: T;
  payload: P;
}

/* ── Private (user-queue) events ───────────────────────────────────────── */

export interface SecondaryMatchPayload {
  answerType: SecondaryAnswerType;
  matchedValue: string;
  alreadyFound: boolean;
}

export interface AnswerRejectedPayload {
  reason: AnswerRejectionReason;
  retryAt: string | null;
}

export interface CurrentRoundProjection {
  roundIndex: number;
  status: RoundStatus;
  startsAt: string | null;
  deadline: string | null;
  playbackUrl: string | null;
  excerptStartSeconds: number | null;
  excerptDurationSeconds: number | null;
  outcome: RoundOutcome | null;
  title: string | null;
  artist: string | null;
  composer: string | null;
  album: string | null;
  streamingLinks: RevealStreamingLinks;
  coverUrl: string | null;
  advanceAt: string | null;
  roundPoints: number;
}

export interface CurrentSnapshotPayload {
  gameStatus: GameStatus;
  currentRoundIndex: number;
  totalRounds: number;
  totalScore: number;
  currentRound: CurrentRoundProjection | null;
  discoveredAnswerTypes: SecondaryAnswerType[];
  expiresAt: string;
}

export interface GameErrorPayload {
  code: string;
  message: string;
  recoverable: boolean;
}

interface PrivateEvent<T extends string, P> {
  metadata: SessionEventMetadata;
  recipientParticipantId: string;
  commandId: string | null;
  type: T;
  payload: P;
}

export type PrivateSessionEvent =
  | PrivateEvent<'SECONDARY_MATCH', SecondaryMatchPayload>
  | PrivateEvent<'ANSWER_REJECTED', AnswerRejectedPayload>
  | PrivateEvent<'CURRENT_SNAPSHOT', CurrentSnapshotPayload>
  | PrivateEvent<'ERROR', GameErrorPayload>;

/* ── Helpers ──────────────────────────────────────────────────────────── */

/** The next optimistic version a command should carry after seeing an event. */
export function advanceSessionVersion(
  current: number,
  event: PublicSessionEvent | PrivateSessionEvent,
): number {
  return Math.max(current, event.metadata.sessionVersion);
}
