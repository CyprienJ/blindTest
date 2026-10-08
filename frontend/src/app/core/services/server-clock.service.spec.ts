import { TestBed } from '@angular/core/testing';
import { ServerClockService } from './server-clock.service';

const NOW = Date.parse('2026-09-02T12:00:00.000Z');

describe('ServerClockService', () => {
  let clock: ServerClockService;

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(NOW));
    clock = TestBed.inject(ServerClockService);
  });

  afterEach(() => jasmine.clock().uninstall());

  const at = (offsetMs: number): string => new Date(NOW + offsetMs).toISOString();

  it('follows the device clock until a server timestamp arrives', () => {
    expect(clock.now()).toBe(NOW);
    expect(clock.offset).toBe(0);
  });

  it('applies the offset of a device clock running behind or ahead', () => {
    clock.observe(at(3_000));
    expect(clock.now()).toBe(NOW + 3_000);

    TestBed.resetTestingModule();
    const ahead = TestBed.inject(ServerClockService);
    ahead.observe(at(-2_000));
    expect(ahead.now()).toBe(NOW - 2_000);
  });

  it('keeps the least delayed sample, since transit time only lowers an estimate', () => {
    clock.observe(at(1_000));
    clock.observe(at(800));
    clock.observe(at(950));

    expect(clock.offset).toBe(1_000);
  });

  it('forgets old samples so a corrected device clock is picked up', () => {
    clock.observe(at(5_000));
    for (let index = 0; index < 16; index++) {
      clock.observe(at(100));
    }

    expect(clock.offset).toBe(100);
  });

  it('ignores unparseable timestamps', () => {
    clock.observe('not a date');

    expect(clock.offset).toBe(0);
  });
});
