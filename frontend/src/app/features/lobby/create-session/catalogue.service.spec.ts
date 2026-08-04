import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogueService } from './catalogue.service';

describe('CatalogueService', () => {
  let service: CatalogueService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CatalogueService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads public playlist summaries and ordered detail', () => {
    service.playlists().subscribe();
    const list = http.expectOne('/api/playlists');
    expect(list.request.method).toBe('GET');
    list.flush([]);

    service.playlist('playlist-id').subscribe();
    const detail = http.expectOne('/api/playlists/playlist-id');
    expect(detail.request.method).toBe('GET');
    detail.flush({ tracks: [] });
  });

  it('sends bounded catalogue filters without blank values', () => {
    service.tracks(2, 15, '  synth  ', 'Pop', 1980).subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/tracks' &&
        candidate.params.get('page') === '2' &&
        candidate.params.get('size') === '15' &&
        candidate.params.get('q') === 'synth' &&
        candidate.params.get('category') === 'Pop' &&
        candidate.params.get('decade') === '1980',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ content: [] });
  });
});
