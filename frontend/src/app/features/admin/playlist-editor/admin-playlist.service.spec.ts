import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminPlaylistService } from './admin-playlist.service';

describe('AdminPlaylistService', () => {
  let service: AdminPlaylistService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminPlaylistService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the playlist metadata endpoints', () => {
    const metadata = { name: 'Synthwave', description: null };

    service.list().subscribe();
    const list = http.expectOne('/api/admin/playlists');
    expect(list.request.method).toBe('GET');
    list.flush([]);

    service.get('playlist-id').subscribe();
    const detail = http.expectOne('/api/admin/playlists/playlist-id');
    expect(detail.request.method).toBe('GET');
    detail.flush({});

    service.create(metadata).subscribe();
    const create = http.expectOne('/api/admin/playlists');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(metadata);
    create.flush({});

    service.update('playlist-id', metadata).subscribe();
    const update = http.expectOne('/api/admin/playlists/playlist-id');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual(metadata);
    update.flush({});

    service.delete('playlist-id').subscribe();
    const remove = http.expectOne('/api/admin/playlists/playlist-id');
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null);
  });

  it('uses explicit insertion, removal and complete-order requests', () => {
    service.addTrack('playlist-id', 'track-id', 3).subscribe();
    const add = http.expectOne('/api/admin/playlists/playlist-id/tracks');
    expect(add.request.method).toBe('POST');
    expect(add.request.body).toEqual({ trackId: 'track-id', order: 3 });
    add.flush({});

    service.removeTrack('playlist-id', 'track-id').subscribe();
    const remove = http.expectOne('/api/admin/playlists/playlist-id/tracks/track-id');
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null);

    service.reorder('playlist-id', ['second-id', 'first-id']).subscribe();
    const reorder = http.expectOne('/api/admin/playlists/playlist-id/tracks/order');
    expect(reorder.request.method).toBe('PUT');
    expect(reorder.request.body).toEqual({ trackIds: ['second-id', 'first-id'] });
    reorder.flush({});
  });
});
