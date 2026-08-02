import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../../../core/config';
import {
  AdminTrack,
  MediaPreview,
  PageResponse,
  TrackAlias,
  TrackAnswerType,
  TrackStatus,
  TrackUpdate,
  TrackWaveform,
} from './admin-track.model';

@Injectable({ providedIn: 'root' })
export class AdminTrackService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/admin/tracks`;

  list(
    page: number,
    size: number,
    status: TrackStatus | null = null,
    search = '',
  ): Observable<PageResponse<AdminTrack>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    if (search.trim()) {
      params = params.set('search', search.trim());
    }
    return this.http.get<PageResponse<AdminTrack>>(this.baseUrl, { params });
  }

  update(id: string, update: TrackUpdate): Observable<AdminTrack> {
    return this.http.put<AdminTrack>(`${this.baseUrl}/${id}`, update);
  }

  listAliases(trackId: string): Observable<TrackAlias[]> {
    return this.http.get<TrackAlias[]>(`${this.baseUrl}/${trackId}/aliases`);
  }

  saveAlias(
    trackId: string,
    type: TrackAnswerType,
    value: string,
    aliasId?: string,
  ): Observable<TrackAlias> {
    const url = aliasId
      ? `${this.baseUrl}/${trackId}/aliases/${aliasId}`
      : `${this.baseUrl}/${trackId}/aliases`;
    return aliasId
      ? this.http.put<TrackAlias>(url, { type, value })
      : this.http.post<TrackAlias>(url, { type, value });
  }

  deleteAlias(trackId: string, aliasId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${trackId}/aliases/${aliasId}`);
  }

  preview(id: string): Observable<MediaPreview> {
    return this.http.get<MediaPreview>(`${this.baseUrl}/${id}/preview-url`);
  }

  waveform(id: string): Observable<TrackWaveform> {
    return this.http.get<TrackWaveform>(`${this.baseUrl}/${id}/waveform`);
  }

  validate(id: string): Observable<AdminTrack> {
    return this.http.post<AdminTrack>(`${this.baseUrl}/${id}/validate`, {});
  }

  reject(id: string): Observable<AdminTrack> {
    return this.http.post<AdminTrack>(`${this.baseUrl}/${id}/reject`, {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
