import { parseImportRealtimeEvent } from './import-realtime.service';

describe('parseImportRealtimeEvent', () => {
  it('parses progress, completion and safe failure events', () => {
    expect(
      parseImportRealtimeEvent({
        body: '{"jobId":"job-1","status":"DOWNLOADING","progress":42}',
      }),
    ).toEqual({
      kind: 'progress',
      jobId: 'job-1',
      status: 'DOWNLOADING',
      progress: 42,
    });
    expect(parseImportRealtimeEvent({ body: '{"jobId":"job-1","trackId":"track-1"}' })).toEqual({
      kind: 'done',
      jobId: 'job-1',
      trackId: 'track-1',
    });
    expect(
      parseImportRealtimeEvent({
        body: '{"jobId":"job-1","errorMessage":"The media import failed"}',
      }),
    ).toEqual({
      kind: 'failed',
      jobId: 'job-1',
      errorMessage: 'The media import failed',
    });
  });
});
