import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../config';
import {
  AdminPlaylist,
  AdminPlaylistDetail,
  AdminTrack,
  AdminUser,
  ImportJob,
  ImportStartResponse,
  ImportTrackPayload,
  Page,
} from '../models/admin.model';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  getTracks(page = 0, size = 100): Observable<Page<AdminTrack>> {
    return this.http.get<Page<AdminTrack>>(`${API_BASE}/admin/tracks`, {
      params: { page, size },
    });
  }

  validateTrack(id: string): Observable<AdminTrack> {
    return this.http.post<AdminTrack>(`${API_BASE}/admin/tracks/${id}/validate`, {});
  }

  rejectTrack(id: string): Observable<AdminTrack> {
    return this.http.post<AdminTrack>(`${API_BASE}/admin/tracks/${id}/reject`, {});
  }

  deleteTrack(id: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/admin/tracks/${id}`);
  }

  startImport(payload: ImportTrackPayload): Observable<ImportStartResponse> {
    return this.http.post<ImportStartResponse>(`${API_BASE}/admin/tracks/import`, payload);
  }

  getImportJob(jobId: string): Observable<ImportJob> {
    return this.http.get<ImportJob>(`${API_BASE}/admin/tracks/import/${jobId}`);
  }

  getPlaylists(): Observable<AdminPlaylist[]> {
    return this.http.get<AdminPlaylist[]>(`${API_BASE}/admin/playlists`);
  }

  createPlaylist(name: string, description?: string): Observable<AdminPlaylist> {
    return this.http.post<AdminPlaylist>(`${API_BASE}/admin/playlists`, { name, description });
  }

  getPlaylist(id: string): Observable<AdminPlaylistDetail> {
    return this.http.get<AdminPlaylistDetail>(`${API_BASE}/admin/playlists/${id}`);
  }

  updatePlaylist(id: string, data: Partial<{ name: string; description: string }>): Observable<AdminPlaylist> {
    return this.http.put<AdminPlaylist>(`${API_BASE}/admin/playlists/${id}`, data);
  }

  deletePlaylist(id: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/admin/playlists/${id}`);
  }

  addTrackToPlaylist(playlistId: string, trackId: string, order: number): Observable<AdminPlaylistDetail> {
    return this.http.post<AdminPlaylistDetail>(
      `${API_BASE}/admin/playlists/${playlistId}/tracks`,
      { trackId, order },
    );
  }

  removeTrackFromPlaylist(playlistId: string, trackId: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/admin/playlists/${playlistId}/tracks/${trackId}`);
  }

  getUsers(): Observable<AdminUser[]> {
    return this.http.get<AdminUser[]>(`${API_BASE}/admin/users`);
  }
}
