import { TestBed } from '@angular/core/testing';
import {
  AUDIO_ELEMENT,
  AudioPlaybackService,
  AudioPlaybackStatus,
  NextRoundPreload,
  RoundPlaybackRequest,
} from './audio-playback.service';
import { ServerClockService } from './server-clock.service';

const NOW = Date.parse('2026-09-02T12:00:00.000Z');

function round(overrides: Partial<RoundPlaybackRequest> = {}): RoundPlaybackRequest {
  return {
    url: 'https://media.example/excerpt.mp3?sig=abc',
    startsAt: new Date(NOW).toISOString(),
    deadline: new Date(NOW + 30_000).toISOString(),
    excerptStartSeconds: 15,
    ...overrides,
  };
}

class FakeAudio extends EventTarget {
  preload = '';
  currentTime = 0;
  volume = 1;
  paused = true;
  readyState: number = HTMLMediaElement.HAVE_METADATA;
  src = '';
  load = jasmine.createSpy('load');
  playResult: () => Promise<void> = () => Promise.resolve();
  playCalls: string[] = [];

  play(): Promise<void> {
    this.playCalls.push(this.src);
    this.paused = false;
    return this.playResult();
  }
  pause = jasmine.createSpy('pause').and.callFake(() => {
    this.paused = true;
  });
  removeAttribute(name: string): void {
    if (name === 'src') {
      this.src = '';
    }
  }
  get currentSrc(): string {
    return this.src;
  }
  emit(type: string): void {
    this.dispatchEvent(new Event(type));
  }
}

describe('AudioPlaybackService', () => {
  let audio: FakeAudio;
  /** Every element the service created: the playing one first, then the standby one. */
  let elements: FakeAudio[];
  let service: AudioPlaybackService;
  let statuses: AudioPlaybackStatus[];

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(NOW));
    elements = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AUDIO_ELEMENT,
          useValue: () => {
            const element = new FakeAudio();
            elements.push(element);
            return element;
          },
        },
      ],
    });
    service = TestBed.inject(AudioPlaybackService);
    audio = elements[0];
    statuses = [];
    service.status$.subscribe((status) => statuses.push(status));
  });

  afterEach(() => jasmine.clock().uninstall());

  async function flush(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
  }

  it('starts immediately and seeks past elapsed time when startsAt is in the past', async () => {
    jasmine.clock().mockDate(new Date(NOW + 4_000));
    service.scheduleRound(round());
    await flush();

    expect(audio.playCalls.length).toBe(1);
    expect(audio.currentTime).toBeCloseTo(19, 3);
    expect(statuses).toEqual(['idle', 'loading', 'playing']);
  });

  it('fades the excerpt in over half a second once playback starts', async () => {
    service.scheduleRound(round());
    expect(audio.volume).toBe(0);
    await flush();

    jasmine.clock().tick(250);
    expect(audio.volume).toBeCloseTo(0.5, 1);
    jasmine.clock().tick(250);
    expect(audio.volume).toBe(1);
  });

  it('restarts the fade-in silently for the next round', async () => {
    service.scheduleRound(round());
    await flush();
    jasmine.clock().tick(500);

    service.scheduleRound(round({ url: 'https://media.example/next.mp3' }));
    expect(audio.volume).toBe(0);
    await flush();
    jasmine.clock().tick(500);
    expect(audio.volume).toBe(1);
  });

  it('defers playback until the scheduled start instant', async () => {
    service.scheduleRound(round({ startsAt: new Date(NOW + 2_000).toISOString() }));
    await flush();
    expect(audio.playCalls.length).toBe(0);

    jasmine.clock().tick(2_000);
    await flush();
    expect(audio.playCalls.length).toBe(1);
    expect(audio.currentTime).toBeCloseTo(15, 3);
  });

  it('never starts a round that is already over', async () => {
    jasmine.clock().mockDate(new Date(NOW + 31_000));
    service.scheduleRound(round());
    await flush();

    expect(audio.playCalls.length).toBe(0);
    expect(statuses.at(-1)).toBe('ended');
  });

  it('reports blocked autoplay and recovers on resume', async () => {
    audio.playResult = () => Promise.reject(new DOMException('blocked', 'NotAllowedError'));
    service.scheduleRound(round());
    await flush();
    expect(statuses.at(-1)).toBe('blocked');

    audio.playResult = () => Promise.resolve();
    service.resume();
    await flush();
    expect(statuses.at(-1)).toBe('playing');
    expect(audio.playCalls.length).toBe(2);
  });

  it('waits for metadata before seeking and playing', async () => {
    audio.readyState = 0;
    service.scheduleRound(round());
    await flush();
    expect(audio.playCalls.length).toBe(0);

    audio.readyState = HTMLMediaElement.HAVE_METADATA;
    audio.emit('loadedmetadata');
    await flush();
    expect(audio.playCalls.length).toBe(1);
  });

  it('stops the previous round before the next one so audio cannot bleed over', async () => {
    service.scheduleRound(round());
    await flush();
    audio.pause.calls.reset();

    service.scheduleRound(round({ url: 'https://media.example/next.mp3' }));
    await flush();

    expect(audio.pause).toHaveBeenCalled();
    expect(audio.playCalls.at(-1)).toBe('https://media.example/next.mp3');
  });

  it('keeps playing past the round deadline so the reveal keeps the music', async () => {
    service.scheduleRound(round());
    await flush();
    audio.pause.calls.reset();

    jasmine.clock().tick(30_000 + 5_000);
    expect(audio.pause).not.toHaveBeenCalled();
    expect(statuses.at(-1)).toBe('playing');
  });

  it('silences a paused round and restarts it on the moved timeline', async () => {
    service.scheduleRound(round());
    await flush();
    jasmine.clock().mockDate(new Date(NOW + 5_000));

    service.pause();
    expect(audio.paused).toBeTrue();
    expect(statuses.at(-1)).toBe('paused');
    expect(audio.src).toBe('https://media.example/excerpt.mp3?sig=abc');
    jasmine.clock().tick(60_000);
    expect(audio.playCalls.length).toBe(1);

    // Resumed 60 s later: the server moved the round, so 5 s have still elapsed.
    jasmine.clock().mockDate(new Date(NOW + 65_000));
    service.scheduleRound(
      round({
        url: 'https://media.example/excerpt.mp3?sig=fresh',
        startsAt: new Date(NOW + 60_000).toISOString(),
        deadline: new Date(NOW + 90_000).toISOString(),
      }),
    );
    await flush();
    expect(audio.src).toBe('https://media.example/excerpt.mp3?sig=fresh');
    expect(audio.currentTime).toBe(20);
    expect(statuses.at(-1)).toBe('playing');
  });

  it('releases the media element on stop', async () => {
    service.scheduleRound(round());
    await flush();

    service.stop();
    expect(audio.pause).toHaveBeenCalled();
    expect(audio.src).toBe('');
    expect(audio.load).toHaveBeenCalled();
    expect(statuses.at(-1)).toBe('idle');
  });

  it('surfaces media load failures', async () => {
    const errors: (string | null)[] = [];
    service.error$.subscribe((message) => errors.push(message));

    service.scheduleRound(round());
    await flush();
    audio.emit('error');

    expect(statuses.at(-1)).toBe('error');
    expect(errors.at(-1)).toBe('The audio could not be loaded.');
  });

  it('ends when the media element finishes', async () => {
    service.scheduleRound(round());
    await flush();
    audio.emit('ended');
    expect(statuses.at(-1)).toBe('ended');
  });

  it('loops a window shorter than the round back to the excerpt start', async () => {
    service.scheduleRound(round({ excerptDurationSeconds: 10 }));
    await flush();

    audio.currentTime = 24.9;
    audio.emit('timeupdate');
    expect(audio.currentTime).toBe(24.9);

    audio.currentTime = 25;
    audio.emit('timeupdate');
    expect(audio.currentTime).toBe(15);
    expect(statuses.at(-1)).toBe('playing');
  });

  it('joins a looping window at the matching position after elapsed time', async () => {
    jasmine.clock().mockDate(new Date(NOW + 23_000));
    service.scheduleRound(round({ excerptDurationSeconds: 10 }));
    await flush();

    expect(audio.currentTime).toBe(18);
  });

  it('restarts the window when the media ends inside it', async () => {
    service.scheduleRound(round({ excerptDurationSeconds: 10 }));
    await flush();
    const playsBefore = audio.playCalls.length;

    audio.currentTime = 20;
    audio.emit('ended');
    await flush();

    expect(audio.currentTime).toBe(15);
    expect(audio.playCalls.length).toBe(playsBefore + 1);
    expect(statuses.at(-1)).toBe('playing');
  });

  it('lets the track play on once the round is revealed instead of restarting the excerpt', async () => {
    service.scheduleRound(round({ excerptDurationSeconds: 10 }));
    await flush();

    service.releaseLoop();
    audio.currentTime = 25;
    audio.emit('timeupdate');

    expect(audio.currentTime).toBe(25);
    expect(statuses.at(-1)).toBe('playing');
  });

  it('stops looping once the round deadline has passed', async () => {
    service.scheduleRound(round({ excerptDurationSeconds: 10 }));
    await flush();

    jasmine.clock().mockDate(new Date(NOW + 30_000));
    audio.currentTime = 25;
    audio.emit('timeupdate');
    expect(audio.currentTime).toBe(25);

    audio.emit('ended');
    expect(statuses.at(-1)).toBe('ended');
  });

  it('loops again for the next round after a release', async () => {
    service.scheduleRound(round({ excerptDurationSeconds: 10 }));
    await flush();
    service.releaseLoop();

    service.scheduleRound(round({ excerptDurationSeconds: 10 }));
    await flush();
    audio.currentTime = 25;
    audio.emit('timeupdate');

    expect(audio.currentTime).toBe(15);
  });

  describe('on the server timeline', () => {
    it('seeks by the server clock rather than the device clock', async () => {
      // The device clock runs three seconds behind the server.
      TestBed.inject(ServerClockService).observe(new Date(NOW + 3_000).toISOString());

      service.scheduleRound(round());
      await flush();

      expect(audio.currentTime).toBeCloseTo(18, 3);
    });

    it('never starts a round the server already ended, whatever the device clock says', async () => {
      TestBed.inject(ServerClockService).observe(new Date(NOW + 31_000).toISOString());

      service.scheduleRound(round());
      await flush();

      expect(audio.playCalls.length).toBe(0);
      expect(statuses.at(-1)).toBe('ended');
    });
  });

  describe('next-round preloading', () => {
    const FRESH_URL = 'https://media.example/next.mp3?sig=fresh';

    function next(overrides: Partial<NextRoundPreload> = {}): NextRoundPreload {
      return {
        roundIndex: 1,
        url: 'https://media.example/next.mp3?sig=early',
        excerptStartSeconds: 42,
        validUntil: new Date(NOW + 120_000).toISOString(),
        ...overrides,
      };
    }

    function nextRound(overrides: Partial<RoundPlaybackRequest> = {}): RoundPlaybackRequest {
      return round({
        roundIndex: 1,
        url: FRESH_URL,
        startsAt: new Date(NOW + 10_000).toISOString(),
        deadline: new Date(NOW + 40_000).toISOString(),
        excerptStartSeconds: 42,
        ...overrides,
      });
    }

    async function revealThenStartNext(
      preload: NextRoundPreload,
      started = nextRound(),
    ): Promise<FakeAudio> {
      service.scheduleRound(round({ roundIndex: 0 }));
      await flush();
      service.preloadNext(preload);
      const standby = elements[1];
      standby.load.calls.reset();
      jasmine.clock().mockDate(new Date(NOW + 10_000));
      service.scheduleRound(started);
      await flush();
      await flush();
      return standby;
    }

    it('buffers the next excerpt on a standby element while the current track plays', async () => {
      service.scheduleRound(round({ roundIndex: 0 }));
      await flush();

      service.preloadNext(next());

      const standby = elements[1];
      expect(standby.src).toBe(next().url);
      expect(standby.load).toHaveBeenCalled();
      expect(standby.currentTime).toBe(42);
      expect(audio.paused).toBeFalse();
      expect(audio.src).toBe(round().url);
    });

    it('seeks the standby element once its metadata arrives', () => {
      service.preloadNext(next());
      const standby = elements[1];
      expect(standby.currentTime).toBe(42);

      service.preloadNext(next({ url: 'https://media.example/other.mp3', excerptStartSeconds: 7 }));
      standby.readyState = HTMLMediaElement.HAVE_NOTHING;
      service.preloadNext(next({ url: 'https://media.example/third.mp3', excerptStartSeconds: 9 }));
      expect(standby.currentTime).toBe(7);
      standby.emit('loadedmetadata');
      expect(standby.currentTime).toBe(9);
    });

    it('starts the next round on the buffered element without loading its fresh URL', async () => {
      const standby = await revealThenStartNext(next());

      expect(standby.playCalls).toEqual([next().url]);
      expect(standby.load).not.toHaveBeenCalled();
      expect(audio.paused).toBeTrue();
      expect(audio.src).toBe('');
      expect(statuses.at(-1)).toBe('playing');
    });

    it('loads the fresh URL when the buffered one may expire before the round ends', async () => {
      const standby = await revealThenStartNext(
        next({ validUntil: new Date(NOW + 39_000).toISOString() }),
      );

      expect(standby.playCalls).toEqual([]);
      expect(standby.src).toBe('');
      expect(audio.playCalls.at(-1)).toBe(FRESH_URL);
    });

    it('ignores media buffered for another round', async () => {
      const standby = await revealThenStartNext(next({ roundIndex: 2 }));

      expect(standby.playCalls).toEqual([]);
      expect(audio.playCalls.at(-1)).toBe(FRESH_URL);
    });

    it('falls back to the allowed element when the browser refuses the buffered one', async () => {
      service.scheduleRound(round({ roundIndex: 0 }));
      await flush();
      service.preloadNext(next());
      const standby = elements[1];
      standby.playResult = () => Promise.reject(new DOMException('gesture', 'NotAllowedError'));
      jasmine.clock().mockDate(new Date(NOW + 10_000));

      service.scheduleRound(nextRound());
      for (let index = 0; index < 4; index++) {
        await flush();
      }

      expect(standby.src).toBe('');
      expect(audio.playCalls.at(-1)).toBe(FRESH_URL);
      expect(audio.currentTime).toBeCloseTo(42, 3);
      expect(statuses.at(-1)).toBe('playing');
    });

    it('treats a failed standby preload as a lost head start, not a playback error', async () => {
      service.scheduleRound(round({ roundIndex: 0 }));
      await flush();
      service.preloadNext(next());

      elements[1].emit('error');

      expect(statuses.at(-1)).toBe('playing');
    });

    it('unlocks the standby element on the first user gesture only', () => {
      document.dispatchEvent(new Event('pointerdown'));
      document.dispatchEvent(new Event('keydown'));

      expect(elements.length).toBe(2);
      expect(elements[1].load).toHaveBeenCalledTimes(1);
    });

    it('releases buffered media on stop', () => {
      service.preloadNext(next());

      service.stop();

      expect(elements[1].src).toBe('');
    });
  });
});
