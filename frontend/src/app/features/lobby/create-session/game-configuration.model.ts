export const GAME_CONFIGURATION_LIMITS = {
  trackCount: {
    minimum: 5,
    maximum: 30,
    increment: 1,
    defaultValue: 15,
  },
  roundDurationSeconds: {
    minimum: 10,
    maximum: 60,
    increment: 5,
    defaultValue: 30,
  },
} as const;

export function isAllowedTrackCount(value: number): boolean {
  const limits = GAME_CONFIGURATION_LIMITS.trackCount;
  return (
    Number.isInteger(value) &&
    value >= limits.minimum &&
    value <= limits.maximum &&
    (value - limits.minimum) % limits.increment === 0
  );
}

export function isAllowedRoundDuration(value: number): boolean {
  const limits = GAME_CONFIGURATION_LIMITS.roundDurationSeconds;
  return (
    Number.isInteger(value) &&
    value >= limits.minimum &&
    value <= limits.maximum &&
    (value - limits.minimum) % limits.increment === 0
  );
}
