import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminImportService } from './admin-import.service';
import { ImportTrackRequest } from './track-import.model';

describe('AdminImportService', () => {
  let service: AdminImportService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminImportService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts an import from a YouTube URL', () => {
    const request = {
      youtubeUrl: 'https://youtu.be/dQw4w9WgXcQ',
    } satisfies ImportTrackRequest;

    service.start(request).subscribe((response) => expect(response.jobId).toBe('job-id'));

    const pending = http.expectOne('/api/admin/tracks/import');
    expect(pending.request.method).toBe('POST');
    expect(pending.request.body).toEqual(request);
    pending.flush({ jobId: 'job-id' });
  });

  it('uploads a file as multipart form data with progress events and trimmed metadata', () => {
    const file = new File([new Uint8Array([0x49, 0x44, 0x33])], 'song.flac', {
      type: 'audio/flac',
    });

    service.upload(file, { title: '  Billie Jean ', artist: '   ' }).subscribe();

    const pending = http.expectOne('/api/admin/tracks/import/upload');
    expect(pending.request.method).toBe('POST');
    expect(pending.request.reportProgress).toBeTrue();
    const body = pending.request.body as FormData;
    expect((body.get('file') as File).name).toBe('song.flac');
    expect(body.get('title')).toBe('Billie Jean');
    expect(body.has('artist')).toBeFalse();
    pending.flush({ jobId: 'job-id' });
  });

  it('polls a durable job snapshot', () => {
    service.getJob('job-id').subscribe((job) => expect(job.status).toBe('DONE'));

    const pending = http.expectOne('/api/admin/tracks/import/job-id');
    expect(pending.request.method).toBe('GET');
    pending.flush({ status: 'DONE' });
  });
});
