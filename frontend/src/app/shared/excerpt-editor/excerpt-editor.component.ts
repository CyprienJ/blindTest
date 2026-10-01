import { Component, computed, ElementRef, input, model, output, viewChild } from '@angular/core';

export const EXCERPT_WINDOW_LIMITS = { minimum: 5, maximum: 60 } as const;

/** Formats whole seconds as m:ss. */
export function formatExcerptTime(seconds: number): string {
  const safeSeconds = Math.max(0, Math.floor(seconds));
  return `${Math.floor(safeSeconds / 60)}:${String(safeSeconds % 60).padStart(2, '0')}`;
}

/**
 * Picks a track's game excerpt: a start offset and a window length, over an optional
 * waveform, with a preview player that loops the window like a game round does.
 */
@Component({
  selector: 'app-excerpt-editor',
  standalone: true,
  templateUrl: './excerpt-editor.component.html',
  styleUrl: './excerpt-editor.component.scss',
})
export class ExcerptEditorComponent {
  /** Signed preview URL; the player is hidden until it is available. */
  readonly previewUrl = input<string | null>(null);
  /** Waveform peaks from 0 to 100; the waveform is hidden when empty. */
  readonly peaks = input<number[]>([]);
  readonly durationSeconds = input<number | null>(null);
  readonly disabled = input(false);
  readonly start = model(0);
  readonly window = model<number>(EXCERPT_WINDOW_LIMITS.maximum);
  /** Emits after any user change to the start or the window. */
  readonly selectionChange = output<void>();
  /** Emits when the browser blocks playback started from a selection change. */
  readonly playbackBlocked = output<void>();

  readonly limits = EXCERPT_WINDOW_LIMITS;
  readonly formatTime = formatExcerptTime;
  private readonly audio = viewChild<ElementRef<HTMLAudioElement>>('audio');

  readonly duration = computed(() => this.durationSeconds() ?? this.window());
  readonly maxStart = computed(() => Math.max(0, this.duration() - this.window()));
  readonly end = computed(() => Math.min(this.duration(), this.start() + this.window()));
  readonly selectionX = computed(() =>
    this.duration() === 0 ? 0 : (this.start() / this.duration()) * 1_000,
  );
  readonly selectionWidth = computed(() =>
    this.duration() === 0 ? 1_000 : Math.min(1_000, (this.window() / this.duration()) * 1_000),
  );
  readonly waveformPath = computed(() => buildWaveformPath(this.peaks()));

  setStart(value: number): void {
    this.start.set(Math.min(this.maxStart(), Math.max(0, Math.round(value))));
    this.selectionChange.emit();
    this.playFromStart();
  }

  setWindow(value: number): void {
    const window = Math.min(
      this.limits.maximum,
      Math.max(this.limits.minimum, Math.round(value) || this.limits.maximum),
    );
    this.window.set(window);
    if (this.start() > this.maxStart()) {
      this.start.set(this.maxStart());
    }
    this.selectionChange.emit();
    this.playFromStart();
  }

  selectWaveformPosition(event: PointerEvent, surface: Element): void {
    if (this.disabled() || (event.type === 'pointermove' && event.buttons !== 1)) {
      return;
    }
    if (event.type === 'pointerdown') {
      surface.setPointerCapture(event.pointerId);
    }
    const bounds = surface.getBoundingClientRect();
    if (bounds.width > 0) {
      const ratio = Math.min(1, Math.max(0, (event.clientX - bounds.left) / bounds.width));
      this.setStart(ratio * this.duration());
    }
  }

  seekToStart(audio: HTMLAudioElement): void {
    audio.currentTime = this.start();
  }

  /** Loops the window, mirroring how a round loops an excerpt shorter than its answer time. */
  loopWindow(audio: HTMLAudioElement): void {
    if (audio.currentTime >= this.end() || audio.currentTime < this.start() - 1) {
      audio.currentTime = this.start();
    }
  }

  restartWindow(audio: HTMLAudioElement): void {
    audio.currentTime = this.start();
    void audio.play().catch(() => undefined);
  }

  private playFromStart(): void {
    const audio = this.audio()?.nativeElement;
    if (!audio) {
      return;
    }
    audio.currentTime = this.start();
    void audio.play().catch((error: unknown) => {
      if (error instanceof DOMException && error.name === 'NotAllowedError') {
        this.playbackBlocked.emit();
      }
    });
  }
}

function buildWaveformPath(peaks: number[]): string {
  if (peaks.length === 0) {
    return '';
  }
  const divisor = Math.max(1, peaks.length - 1);
  const point = (index: number, direction: 1 | -1): string => {
    const peak = Math.min(100, Math.max(0, peaks[index] ?? 0));
    const x = (index * 1_000) / divisor;
    const y = 50 - direction * (peak / 100) * 46;
    return `${x.toFixed(2)} ${y.toFixed(2)}`;
  };
  const top = peaks.map((_, index) => point(index, 1));
  const bottom = peaks.map((_, index) => point(peaks.length - index - 1, -1));
  return `M ${top.join(' L ')} L ${bottom.join(' L ')} Z`;
}
