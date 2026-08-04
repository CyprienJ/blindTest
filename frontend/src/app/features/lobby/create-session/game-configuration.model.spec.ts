import {
  GAME_CONFIGURATION_LIMITS,
  isAllowedRoundDuration,
  isAllowedTrackCount,
} from './game-configuration.model';

describe('game configuration limits', () => {
  it('exposes the approved defaults and increments', () => {
    expect(GAME_CONFIGURATION_LIMITS.trackCount).toEqual({
      minimum: 5,
      maximum: 30,
      increment: 1,
      defaultValue: 15,
    });
    expect(GAME_CONFIGURATION_LIMITS.roundDurationSeconds).toEqual({
      minimum: 10,
      maximum: 60,
      increment: 5,
      defaultValue: 30,
    });
  });

  it('accepts only track counts within the inclusive boundaries', () => {
    expect(isAllowedTrackCount(5)).toBeTrue();
    expect(isAllowedTrackCount(15)).toBeTrue();
    expect(isAllowedTrackCount(30)).toBeTrue();
    expect(isAllowedTrackCount(4)).toBeFalse();
    expect(isAllowedTrackCount(31)).toBeFalse();
    expect(isAllowedTrackCount(5.5)).toBeFalse();
  });

  it('accepts only five-second duration increments within the boundaries', () => {
    expect(isAllowedRoundDuration(10)).toBeTrue();
    expect(isAllowedRoundDuration(30)).toBeTrue();
    expect(isAllowedRoundDuration(60)).toBeTrue();
    expect(isAllowedRoundDuration(9)).toBeFalse();
    expect(isAllowedRoundDuration(11)).toBeFalse();
    expect(isAllowedRoundDuration(61)).toBeFalse();
  });
});
