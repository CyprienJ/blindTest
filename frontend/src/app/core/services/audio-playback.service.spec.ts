import { TestBed } from '@angular/core/testing';
import {
  AUDIO_ELEMENT,
  AudioPlaybackService,
  AudioPlaybackStatus,
  RoundPlaybackRequest,
} from './audio-playback.service';

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
  let service: AudioPlaybackService;
  let statuses: AudioPlaybackStatus[];

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(NOW));
    audio = new FakeAudio();
    TestBed.configureTestingModule({
      providers: [{ provide: AUDIO_ELEMENT, useValue: () => audio }],
    });
    service = TestBed.inject(AudioPlaybackService);
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
});
