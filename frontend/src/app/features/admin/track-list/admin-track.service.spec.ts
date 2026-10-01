import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminTrackService } from './admin-track.service';

describe('AdminTrackService', () => {
  let service: AdminTrackService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminTrackService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends catalogue filters and pagination to the administrator endpoint', () => {
    service.list(2, 10, 'PENDING', '  midnight  ').subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/admin/tracks' &&
        candidate.params.get('page') === '2' &&
        candidate.params.get('size') === '10' &&
        candidate.params.get('status') === 'PENDING' &&
        candidate.params.get('search') === 'midnight',
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 2,
      size: 10,
      first: false,
      last: true,
    });
  });

  it('uses the delivered metadata, alias, preview and workflow endpoints', () => {
    const id = 'track-id';
    const aliasId = 'alias-id';

    service.update(id, {} as never).subscribe();
    http.expectOne(`/api/admin/tracks/${id}`).flush({});

    service.listAliases(id).subscribe();
    http.expectOne(`/api/admin/tracks/${id}/aliases`).flush([]);

    service.saveAlias(id, 'TITLE', 'Alias').subscribe();
    const createAlias = http.expectOne(`/api/admin/tracks/${id}/aliases`);
    expect(createAlias.request.method).toBe('POST');
    createAlias.flush({});

    service.saveAlias(id, 'ALBUM', 'Alias', aliasId).subscribe();
    const updateAlias = http.expectOne(`/api/admin/tracks/${id}/aliases/${aliasId}`);
    expect(updateAlias.request.method).toBe('PUT');
    updateAlias.flush({});

    service.deleteAlias(id, aliasId).subscribe();
    http.expectOne(`/api/admin/tracks/${id}/aliases/${aliasId}`).flush(null);

    service.preview(id).subscribe();
    http.expectOne(`/api/admin/tracks/${id}/preview-url`).flush({});

    service.waveform(id).subscribe();
    http.expectOne(`/api/admin/tracks/${id}/waveform`).flush({ peaks: [] });

    service.validate(id).subscribe();
    http.expectOne(`/api/admin/tracks/${id}/validate`).flush({});

    service.reject(id).subscribe();
    http.expectOne(`/api/admin/tracks/${id}/reject`).flush({});

    service.delete(id).subscribe();
    const deleteTrack = http.expectOne(`/api/admin/tracks/${id}`);
    expect(deleteTrack.request.method).toBe('DELETE');
    deleteTrack.flush(null);
  });

  it('searches, previews and uploads cover candidates through the admin API', () => {
    service.suggestCovers({ title: 'Take On Me', artist: 'a-ha', album: null }).subscribe();
    const search = http.expectOne('/api/admin/cover-suggestions');
    expect(search.request.method).toBe('POST');
    expect(search.request.body).toEqual({ title: 'Take On Me', artist: 'a-ha', album: null });
    search.flush([]);

    service.coverThumbnail('c1').subscribe();
    const thumbnail = http.expectOne('/api/admin/cover-suggestions/c1/thumbnail');
    expect(thumbnail.request.responseType).toBe('blob');
    thumbnail.flush(new Blob());

    service.uploadCover(new File(['x'], 'cover.png')).subscribe();
    const upload = http.expectOne('/api/admin/cover-suggestions/upload');
    expect(upload.request.body instanceof FormData).toBeTrue();
    expect((upload.request.body as FormData).get('file')).toBeInstanceOf(File);
    upload.flush({ candidateId: 'u1', source: 'UPLOAD', album: '', artist: '' });
  });
});
