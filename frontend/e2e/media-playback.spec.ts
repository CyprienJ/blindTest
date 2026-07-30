import { expect, test } from '@playwright/test';

const mediaUrl = 'http://127.0.0.1:4200/media/audio-seek-fixture.wav';
const sampleRate = 8_000;
const durationSeconds = 30;
const chunkSize = 32 * 1024;

test('audio playback supports byte-range seeking', async ({ page }) => {
  const fixture = wavSilence(durationSeconds, sampleRate);
  const rangeStarts: number[] = [];

  await page.route(mediaUrl, async (route) => {
    const range = route.request().headers()['range'];
    if (!range) {
      await route.fulfill({
        status: 200,
        headers: mediaHeaders(fixture.length),
        body: fixture,
      });
      return;
    }

    const match = /^bytes=(\d+)-(\d*)$/.exec(range);
    if (!match) {
      await route.fulfill({ status: 416 });
      return;
    }

    const start = Number(match[1]);
    const requestedEnd = match[2] ? Number(match[2]) : fixture.length - 1;
    const end = Math.min(requestedEnd, start + chunkSize - 1, fixture.length - 1);
    rangeStarts.push(start);
    await route.fulfill({
      status: 206,
      headers: {
        ...mediaHeaders(end - start + 1),
        'Content-Range': `bytes ${start}-${end}/${fixture.length}`,
      },
      body: fixture.subarray(start, end + 1),
    });
  });

  await page.setContent(`<audio aria-label="seek fixture" preload="metadata" src="${mediaUrl}"></audio>`);
  const audio = page.getByLabel('seek fixture');
  await expect
    .poll(() => audio.evaluate((element: HTMLAudioElement) => element.duration))
    .toBeCloseTo(durationSeconds, 0);

  await audio.evaluate((element: HTMLAudioElement) => {
    element.currentTime = 20;
  });

  await expect
    .poll(() => audio.evaluate((element: HTMLAudioElement) => element.currentTime))
    .toBeGreaterThan(19);
  await expect.poll(() => rangeStarts.some((start) => start > 0)).toBe(true);
});

function mediaHeaders(contentLength: number): Record<string, string> {
  return {
    'Accept-Ranges': 'bytes',
    'Cache-Control': 'no-store',
    'Content-Length': contentLength.toString(),
    'Content-Type': 'audio/wav',
  };
}

function wavSilence(seconds: number, rate: number): Buffer {
  const channels = 1;
  const bytesPerSample = 2;
  const dataLength = seconds * rate * channels * bytesPerSample;
  const wav = Buffer.alloc(44 + dataLength);

  wav.write('RIFF', 0);
  wav.writeUInt32LE(36 + dataLength, 4);
  wav.write('WAVE', 8);
  wav.write('fmt ', 12);
  wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20);
  wav.writeUInt16LE(channels, 22);
  wav.writeUInt32LE(rate, 24);
  wav.writeUInt32LE(rate * channels * bytesPerSample, 28);
  wav.writeUInt16LE(channels * bytesPerSample, 32);
  wav.writeUInt16LE(bytesPerSample * 8, 34);
  wav.write('data', 36);
  wav.writeUInt32LE(dataLength, 40);
  return wav;
}
