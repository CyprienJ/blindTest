import { Injectable } from '@angular/core';

/** Recent samples kept, so a corrected device clock replaces the old offset within a few rounds. */
const SAMPLE_WINDOW = 16;

/**
 * Estimates the server clock from the `occurredAt` stamp of every session event, so round
 * timers and audio follow server instants even when the device clock is off.
 *
 * Each sample (server stamp minus arrival time) understates the true offset by that message's
 * transit time, so the largest recent sample is the best estimate.
 */
@Injectable({ providedIn: 'root' })
export class ServerClockService {
  private samples: number[] = [];
  private offsetMs = 0;

  /** Records a server timestamp (ISO-8601) received just now. */
  observe(serverTime: string): void {
    const serverMs = Date.parse(serverTime);
    if (Number.isNaN(serverMs)) {
      return;
    }
    this.samples.push(serverMs - Date.now());
    if (this.samples.length > SAMPLE_WINDOW) {
      this.samples.shift();
    }
    this.offsetMs = Math.max(...this.samples);
  }

  /** Estimated server time in epoch milliseconds. */
  now(): number {
    return Date.now() + this.offsetMs;
  }

  /** Estimated server-minus-device offset in milliseconds. */
  get offset(): number {
    return this.offsetMs;
  }
}
