import { inject, Injectable, InjectionToken, OnDestroy } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ServerClockService } from './server-clock.service';

export type AudioPlaybackStatus =
  | 'idle'
  | 'loading'
  | 'playing'
  | 'paused'
  | 'blocked'
  | 'ended'
  | 'error';

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
  /** Window length looped from the offset; plays straight through when absent. */
  excerptDurationSeconds?: number;
  /** Zero-based round position; lets the round reuse media buffered by `preloadNext`. */
  roundIndex?: number;
}

/** The following round's media, announced with a reveal so it can buffer in advance. */
export interface NextRoundPreload {
  roundIndex: number;
  url: string;
  excerptStartSeconds: number;
  /** Server instant through which the signed URL stays valid (ISO-8601). */
  validUntil: string;
}

/** Factory for the media element — overridable in tests. */
export const AUDIO_ELEMENT = new InjectionToken<() => HTMLAudioElement>('AUDIO_ELEMENT', {
  providedIn: 'root',
  factory: () => () => new Audio(),
});

/** Duration of the volume ramp applied whenever an excerpt starts. */
const FADE_IN_MS = 500;
const FADE_STEP_MS = 25;

/**
 * Owns the round-audio element: preloads the signed excerpt, starts it on the server timeline
 * (seeking past any elapsed time), recovers from blocked autoplay, and guarantees a round's audio
 * is released before the next one. Playback deliberately outlives the round deadline so the
 * reveal keeps the music; the owner stops it explicitly or by scheduling the next round. An
 * excerpt window shorter than the round loops back to its start, mirroring the server's window,
 * but only while the round is open: once it is revealed or its deadline passes, the track plays
 * on from where it is instead of restarting the excerpt.
 *
 * A second, standby element buffers the next round during the reveal and becomes the playing
 * element when that round starts. WebKit only lets an element play without a gesture once it
 * was loaded or played inside one, so the first user gesture unlocks the standby element, and
 * a still-refused standby element falls back to the previous one with the round's fresh URL.
 */
@Injectable({ providedIn: 'root' })
export class AudioPlaybackService implements OnDestroy {
  private readonly createElement = inject(AUDIO_ELEMENT);
  private readonly serverClock = inject(ServerClockService);
  private audio: HTMLAudioElement;
  private standby: HTMLAudioElement | null = null;
  private standbyUnlocked = false;

  private readonly statusSubject = new BehaviorSubject<AudioPlaybackStatus>('idle');
  private readonly errorSubject = new BehaviorSubject<string | null>(null);

  readonly status$ = this.statusSubject.asObservable();
  readonly error$ = this.errorSubject.asObservable();

  private pending: RoundPlaybackRequest | null = null;
  /** Media buffering on the standby element for the next round. */
  private prepared: NextRoundPreload | null = null;
  /** Set while a round plays adopted standby media; undone if that element may not play. */
  private adopted: { previous: HTMLAudioElement; freshUrl: string } | null = null;
  /** Set when the round is revealed; the excerpt then stops looping. */
  private loopReleased = false;
  private startTimer: ReturnType<typeof setTimeout> | null = null;
  private fadeTimer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.audio = this.newElement();
    document.addEventListener('pointerdown', this.unlockStandby, true);
    document.addEventListener('keydown', this.unlockStandby, true);
  }

  /** Buffers a media URL ahead of playback without starting it. */
  preload(url: string): void {
    if (this.audio.src !== url) {
      this.audio.src = url;
      this.audio.load();
    }
  }

  /**
   * Buffers the next round's excerpt on the standby element while the current track keeps
   * playing, so that round can start without waiting for the network.
   */
  preloadNext(next: NextRoundPreload): void {
    const element = this.standbyElement();
    this.prepared = next;
    if (element.src !== next.url) {
      element.src = next.url;
      element.load();
    }
    this.bufferAtExcerpt(element, next);
  }

  /**
   * Plays a round's excerpt aligned to the server timeline. Any previous round's
   * audio is stopped first, so it can never bleed into the new round.
   */
  scheduleRound(request: RoundPlaybackRequest): void {
    this.teardownPlayback();
    this.pending = null;
    this.loopReleased = false;
    this.adopted = null;

    const startMs = Date.parse(request.startsAt);
    const deadlineMs = Date.parse(request.deadline);
    const now = this.serverClock.now();

    if (Number.isNaN(startMs) || Number.isNaN(deadlineMs) || now >= deadlineMs) {
      // The round is already over; never start stale audio.
      this.discardPrepared();
      this.setStatus('ended');
      return;
    }

    const playing = this.adoptPrepared(request);
    this.pending = playing;
    this.errorSubject.next(null);
    this.preload(playing.url);

    if (startMs > now) {
      this.setStatus('loading');
      this.startTimer = setTimeout(() => this.begin(), startMs - now);
    } else {
      this.begin();
    }
  }

  /** Lets the current track play on past its excerpt window, e.g. once the round is revealed. */
  releaseLoop(): void {
    this.loopReleased = true;
  }

  /**
   * Silences a paused round while keeping its media buffered. Playback restarts when the
   * resumed round is scheduled again with its moved server timeline.
   */
  pause(): void {
    this.teardownPlayback();
    this.pending = null;
    this.setStatus('paused');
  }

  /** Retries playback after the browser blocked autoplay — call from a user gesture. */
  resume(): void {
    if (this.pending && this.statusSubject.value === 'blocked') {
      this.begin();
    }
  }

  /** Stops playback and releases the media elements. */
  stop(status: AudioPlaybackStatus = 'idle'): void {
    this.teardownPlayback();
    this.pending = null;
    this.adopted = null;
    this.release(this.audio);
    this.discardPrepared();
    this.setStatus(status);
  }

  ngOnDestroy(): void {
    this.teardownPlayback();
    document.removeEventListener('pointerdown', this.unlockStandby, true);
    document.removeEventListener('keydown', this.unlockStandby, true);
    for (const element of [this.audio, this.standby]) {
      if (element) {
        element.removeEventListener('ended', this.onEnded);
        element.removeEventListener('error', this.onError);
        element.removeEventListener('timeupdate', this.onTimeUpdate);
        this.release(element);
      }
    }
  }

  private newElement(): HTMLAudioElement {
    const element = this.createElement();
    element.preload = 'auto';
    element.addEventListener('ended', this.onEnded);
    element.addEventListener('error', this.onError);
    element.addEventListener('timeupdate', this.onTimeUpdate);
    return element;
  }

  private standbyElement(): HTMLAudioElement {
    return (this.standby ??= this.newElement());
  }

  /** Seeks the buffering element to the excerpt, so the browser fetches the bytes it will play. */
  private bufferAtExcerpt(element: HTMLAudioElement, next: NextRoundPreload): void {
    const seek = (): void => {
      if (this.prepared !== next || element !== this.standby) {
        return;
      }
      try {
        element.currentTime = next.excerptStartSeconds;
      } catch {
        // Not seekable yet; the round seeks again when it starts.
      }
    };
    if (element.readyState >= HTMLMediaElement.HAVE_METADATA) {
      seek();
    } else {
      element.addEventListener('loadedmetadata', seek, { once: true });
    }
  }

  /**
   * Swaps in the standby element when it buffered this round with a URL that outlives the
   * round's deadline; otherwise plays the request's own URL on the current element.
   */
  private adoptPrepared(request: RoundPlaybackRequest): RoundPlaybackRequest {
    const prepared = this.prepared;
    const standby = this.standby;
    this.prepared = null;
    if (
      !prepared ||
      !standby ||
      prepared.roundIndex !== request.roundIndex ||
      !(Date.parse(prepared.validUntil) >= Date.parse(request.deadline))
    ) {
      if (standby) {
        this.release(standby);
      }
      return request;
    }
    const previous = this.audio;
    this.release(previous);
    this.audio = standby;
    this.standby = previous;
    this.adopted = { previous, freshUrl: request.url };
    return { ...request, url: prepared.url };
  }

  /** Returns to the previous, already allowed element with the round's fresh URL. */
  private revertAdoption(request: RoundPlaybackRequest): boolean {
    const adopted = this.adopted;
    if (!adopted || this.pending !== request) {
      return false;
    }
    this.adopted = null;
    this.release(this.audio);
    this.standby = this.audio;
    this.audio = adopted.previous;
    this.pending = { ...request, url: adopted.freshUrl };
    this.preload(adopted.freshUrl);
    this.begin();
    return true;
  }

  private discardPrepared(): void {
    this.prepared = null;
    if (this.standby) {
      this.release(this.standby);
    }
  }

  private release(element: HTMLAudioElement): void {
    if (!element.paused) {
      element.pause();
    }
    element.removeAttribute('src');
    element.load();
  }

  /** Lets the standby element play later without a gesture (see the class comment). */
  private readonly unlockStandby = (): void => {
    if (this.standbyUnlocked) {
      return;
    }
    this.standbyUnlocked = true;
    document.removeEventListener('pointerdown', this.unlockStandby, true);
    document.removeEventListener('keydown', this.unlockStandby, true);
    const element = this.standbyElement();
    element.load();
    if (this.prepared) {
      this.bufferAtExcerpt(element, this.prepared);
    }
  };

  private begin(): void {
    const request = this.pending;
    if (!request) {
      return;
    }
    this.setStatus('loading');

    const element = this.audio;
    const start = (): void => {
      if (this.pending !== request || this.audio !== element) {
        return;
      }
      try {
        // Measured once the media is ready, so a slow load does not start behind the timeline.
        element.currentTime = this.seekTarget(request);
      } catch {
        // Seeking before metadata is ready; the queued value applies on load.
      }
      element.volume = 0;
      void element
        .play()
        .then(() => {
          if (this.pending === request) {
            this.adopted = null;
            this.setStatus('playing');
            this.fadeIn();
          }
        })
        .catch((error: unknown) => {
          if (this.pending !== request) {
            return;
          }
          if (error instanceof DOMException && error.name === 'NotAllowedError') {
            if (!this.revertAdoption(request)) {
              this.setStatus('blocked');
            }
          } else {
            this.fail($localize`Playback could not start.`);
          }
        });
    };

    if (element.readyState >= HTMLMediaElement.HAVE_METADATA) {
      start();
    } else {
      element.addEventListener('loadedmetadata', start, { once: true });
    }
  }

  /** Excerpt offset plus any time that elapsed since the server started the round. */
  private seekTarget(request: RoundPlaybackRequest): number {
    const elapsedSeconds =
      Math.max(0, this.serverClock.now() - Date.parse(request.startsAt)) / 1000;
    const window = this.loopWindow(request);
    return request.excerptStartSeconds + (window ? elapsedSeconds % window : elapsedSeconds);
  }

  private loopWindow(request: RoundPlaybackRequest): number | null {
    const window = request.excerptDurationSeconds;
    return window && window > 0 ? window : null;
  }

  /** Whether the excerpt window should still loop: only while the round is open. */
  private looping(request: RoundPlaybackRequest | null): request is RoundPlaybackRequest {
    return (
      !!request &&
      !!this.loopWindow(request) &&
      !this.loopReleased &&
      this.serverClock.now() < Date.parse(request.deadline)
    );
  }

  /** Loops the excerpt window while the round is open; afterwards the track continues. */
  private readonly onTimeUpdate = (event: Event): void => {
    const request = this.pending;
    if (
      event.target === this.audio &&
      this.looping(request) &&
      this.audio.currentTime >= request.excerptStartSeconds + this.loopWindow(request)!
    ) {
      this.audio.currentTime = request.excerptStartSeconds;
    }
  };

  private fadeIn(): void {
    this.clearFade();
    const element = this.audio;
    const fadeStartMs = Date.now();
    this.fadeTimer = setInterval(() => {
      const progress = Math.min(1, (Date.now() - fadeStartMs) / FADE_IN_MS);
      element.volume = progress;
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

  private readonly onEnded = (event: Event): void => {
    if (event.target !== this.audio) {
      return;
    }
    const request = this.pending;
    if (this.looping(request)) {
      // The media ended inside a window that runs past it: restart the window.
      this.audio.currentTime = request.excerptStartSeconds;
      void this.audio.play().catch(() => this.setStatus('ended'));
      return;
    }
    this.pending = null;
    this.setStatus('ended');
  };

  private readonly onError = (event: Event): void => {
    // A failed standby preload only costs the head start; the round loads its own URL then.
    if (event.target === this.audio && this.audio.currentSrc) {
      this.fail($localize`The audio could not be loaded.`);
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
