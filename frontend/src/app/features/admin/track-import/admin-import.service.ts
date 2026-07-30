import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../../../core/config';
import { ImportJob, ImportStartResponse, ImportTrackRequest } from './track-import.model';

@Injectable({ providedIn: 'root' })
export class AdminImportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/admin/tracks/import`;

  start(request: ImportTrackRequest): Observable<ImportStartResponse> {
    return this.http.post<ImportStartResponse>(this.baseUrl, request);
  }

  getJob(jobId: string): Observable<ImportJob> {
    return this.http.get<ImportJob>(`${this.baseUrl}/${jobId}`);
  }
}
