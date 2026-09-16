import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../../../core/config';
import {
  CataloguePage,
  PublicPlaylist,
  PublicPlaylistDetail,
  PublicTrack,
} from './catalogue.model';

@Injectable({ providedIn: 'root' })
export class CatalogueService {
  private readonly http = inject(HttpClient);

  playlists(): Observable<PublicPlaylist[]> {
    return this.http.get<PublicPlaylist[]>(`${API_BASE}/playlists`);
  }

  playlist(id: string): Observable<PublicPlaylistDetail> {
    return this.http.get<PublicPlaylistDetail>(`${API_BASE}/playlists/${id}`);
  }

  tracks(
    page = 0,
    size = 20,
    query = '',
    category?: string,
    decade?: number,
  ): Observable<CataloguePage<PublicTrack>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (query.trim()) {
      params = params.set('q', query.trim());
    }
    if (category) {
      params = params.set('category', category);
    }
    if (decade !== undefined) {
      params = params.set('decade', decade);
    }
    return this.http.get<CataloguePage<PublicTrack>>(`${API_BASE}/tracks`, { params });
  }
}
