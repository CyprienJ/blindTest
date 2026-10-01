import { inject, Injectable, InjectionToken, OnDestroy } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type AudioPlaybackStatus = 'idle' | 'loading' | 'playing' | 'blocked' | 'ended' | 'error';

/** Authoritative timing for one round's media excerpt (from `ROUND_STARTED` / snapshot). */
export interface RoundPlaybackRequest {
  /** Signed, range-capable media URL. */
  url: string;
  /** Server instant playback should begin (ISO-8601). */
  startsAt: string;
  /** Server instant the round ends (ISO-8601). */
  deadline: string;
  /** Administrator-configured offset into the track, in seconds. */
  excerptStartSeconds: number;
}

/** Factory for the media element — overridable in tests. */
export const AUDIO_ELEMENT = new InjectionToken<() => HTMLAudioElement>('AUDIO_ELEMENT', {
  providedIn: 'root',
  factory: () => () => new Audio(),
});

/**
 * Owns the single round-audio element: preloads the signed excerpt, starts it on
 * the server timeline (seeking past any elapsed time), recovers from blocked
 * autoplay, and guarantees a round's audio is released before the next one.
 * Playback deliberately outlives the round deadline so the reveal keeps the music;
 * the owner stops it explicitly or by scheduling the next round.
 */
/** Duration of the volume ramp applied whenever an excerpt starts. */
const FADE_IN_MS = 500;
const FADE_STEP_MS = 25;

@Injectable({ providedIn: 'root' })
export class AudioPlaybackService implements OnDestroy {
  private readonly audio = inject(AUDIO_ELEMENT)();

  private readonly statusSubject = new BehaviorSubject<AudioPlaybackStatus>('idle');
  private readonly errorSubject = new BehaviorSubject<string | null>(null);

  readonly status$ = this.statusSubject.asObservable();
  readonly error$ = this.errorSubject.asObservable();

  private pending: RoundPlaybackRequest | null = null;
  private startTimer: ReturnType<typeof setTimeout> | null = null;
  private fadeTimer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.audio.preload = 'auto';
    this.audio.addEventListener('ended', this.onEnded);
    this.audio.addEventListener('error', this.onError);
  }

  /** Buffers a media URL ahead of playback without starting it. */
  preload(url: string): void {
    if (this.audio.src !== url) {
      this.audio.src = url;
      this.audio.load();
    }
  }

  /**
   * Plays a round's excerpt aligned to the server timeline. Any previous round's
   * audio is stopped first, so it can never bleed into the new round.
   */
  scheduleRound(request: RoundPlaybackRequest): void {
    this.teardownPlayback();
    this.pending = null;

    const startMs = Date.parse(request.startsAt);
    const deadlineMs = Date.parse(request.deadline);
    const now = Date.now();

    if (Number.isNaN(startMs) || Number.isNaN(deadlineMs) || now >= deadlineMs) {
      // The round is already over; never start stale audio.
      this.setStatus('ended');
      return;
    }

    this.pending = request;
    this.errorSubject.next(null);
    this.preload(request.url);

    if (startMs > now) {
      this.setStatus('loading');
      this.startTimer = setTimeout(() => this.begin(), startMs - now);
    } else {
      this.begin();
    }
  }

  /** Retries playback after the browser blocked autoplay — call from a user gesture. */
  resume(): void {
    if (this.pending && this.statusSubject.value === 'blocked') {
      this.begin();
    }
  }

  /** Stops playback and releases the media element. */
  stop(status: AudioPlaybackStatus = 'idle'): void {
    this.teardownPlayback();
    this.pending = null;
    this.audio.removeAttribute('src');
    this.audio.load();
    this.setStatus(status);
  }

  ngOnDestroy(): void {
    this.teardownPlayback();
    this.audio.removeEventListener('ended', this.onEnded);
    this.audio.removeEventListener('error', this.onError);
    this.audio.removeAttribute('src');
    this.audio.load();
  }

  private begin(): void {
    const request = this.pending;
    if (!request) {
      return;
    }
    this.setStatus('loading');

    const seekSeconds = this.seekTarget(request);
    const start = (): void => {
      try {
        this.audio.currentTime = seekSeconds;
      } catch {
        // Seeking before metadata is ready; the queued value applies on load.
      }
      this.audio.volume = 0;
      void this.audio
        .play()
        .then(() => {
          if (this.pending === request) {
            this.setStatus('playing');
            this.fadeIn();
          }
        })
        .catch((error: unknown) => {
          if (this.pending !== request) {
            return;
          }
          if (error instanceof DOMException && error.name === 'NotAllowedError') {
            this.setStatus('blocked');
          } else {
            this.fail('Playback could not start.');
          }
        });
    };

    if (this.audio.readyState >= HTMLMediaElement.HAVE_METADATA) {
      start();
    } else {
      this.audio.addEventListener('loadedmetadata', start, { once: true });
    }
  }

  /** Excerpt offset plus any time that elapsed since the server started the round. */
  private seekTarget(request: RoundPlaybackRequest): number {
    const elapsedMs = Math.max(0, Date.now() - Date.parse(request.startsAt));
    return request.excerptStartSeconds + elapsedMs / 1000;
  }

  private fadeIn(): void {
    this.clearFade();
    const fadeStartMs = Date.now();
    this.fadeTimer = setInterval(() => {
      const progress = Math.min(1, (Date.now() - fadeStartMs) / FADE_IN_MS);
      this.audio.volume = progress;
      if (progress >= 1) {
        this.clearFade();
      }
    }, FADE_STEP_MS);
  }

  private clearFade(): void {
    if (this.fadeTimer) {
      clearInterval(this.fadeTimer);
      this.fadeTimer = null;
    }
  }

  private teardownPlayback(): void {
    this.clearFade();
    if (this.startTimer) {
      clearTimeout(this.startTimer);
      this.startTimer = null;
    }
    if (!this.audio.paused) {
      this.audio.pause();
    }
  }

  private readonly onEnded = (): void => {
    this.pending = null;
    this.setStatus('ended');
  };

  private readonly onError = (): void => {
    if (this.audio.currentSrc) {
      this.fail('The audio could not be loaded.');
    }
  };

  private fail(message: string): void {
    this.teardownPlayback();
    this.pending = null;
    this.errorSubject.next(message);
    this.setStatus('error');
  }

  private setStatus(status: AudioPlaybackStatus): void {
    if (this.statusSubject.value !== status) {
      this.statusSubject.next(status);
    }
  }
}
