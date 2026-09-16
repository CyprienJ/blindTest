import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../../../core/config';
import {
  AdminPlaylistDetail,
  AdminPlaylistSummary,
  PlaylistMetadata,
} from './admin-playlist.model';

@Injectable({ providedIn: 'root' })
export class AdminPlaylistService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/admin/playlists`;

  list(): Observable<AdminPlaylistSummary[]> {
    return this.http.get<AdminPlaylistSummary[]>(this.baseUrl);
  }

  get(id: string): Observable<AdminPlaylistDetail> {
    return this.http.get<AdminPlaylistDetail>(`${this.baseUrl}/${id}`);
  }

  create(metadata: PlaylistMetadata): Observable<AdminPlaylistSummary> {
    return this.http.post<AdminPlaylistSummary>(this.baseUrl, metadata);
  }

  update(id: string, metadata: PlaylistMetadata): Observable<AdminPlaylistSummary> {
    return this.http.put<AdminPlaylistSummary>(`${this.baseUrl}/${id}`, metadata);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  addTrack(playlistId: string, trackId: string, order: number): Observable<AdminPlaylistDetail> {
    return this.http.post<AdminPlaylistDetail>(`${this.baseUrl}/${playlistId}/tracks`, {
      trackId,
      order,
    });
  }

  removeTrack(playlistId: string, trackId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${playlistId}/tracks/${trackId}`);
  }

  reorder(playlistId: string, trackIds: string[]): Observable<AdminPlaylistDetail> {
    return this.http.put<AdminPlaylistDetail>(`${this.baseUrl}/${playlistId}/tracks/order`, {
      trackIds,
    });
  }
}
