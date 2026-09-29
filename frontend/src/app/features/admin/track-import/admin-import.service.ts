import { HttpClient, HttpEvent } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../../../core/config';
import {
  ImportJob,
  ImportStartResponse,
  ImportTrackRequest,
  UploadTrackMetadata,
} from './track-import.model';

@Injectable({ providedIn: 'root' })
export class AdminImportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/admin/tracks/import`;

  start(request: ImportTrackRequest): Observable<ImportStartResponse> {
    return this.http.post<ImportStartResponse>(this.baseUrl, request);
  }

  /** Uploads an audio file; blank metadata is omitted so the server falls back to file tags. */
  upload(file: File, metadata: UploadTrackMetadata): Observable<HttpEvent<ImportStartResponse>> {
    const body = new FormData();
    body.append('file', file, file.name);
    const title = metadata.title.trim();
    const artist = metadata.artist.trim();
    if (title) {
      body.append('title', title);
    }
    if (artist) {
      body.append('artist', artist);
    }
    return this.http.post<ImportStartResponse>(`${this.baseUrl}/upload`, body, {
      observe: 'events',
      reportProgress: true,
    });
  }

  getJob(jobId: string): Observable<ImportJob> {
    return this.http.get<ImportJob>(`${this.baseUrl}/${jobId}`);
  }
}
