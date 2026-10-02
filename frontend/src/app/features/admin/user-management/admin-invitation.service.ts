import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../../../core/config';
import { AdminInvitation } from './admin-invitation.model';

@Injectable({ providedIn: 'root' })
export class AdminInvitationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/admin/invitations`;

  list(): Observable<AdminInvitation[]> {
    return this.http.get<AdminInvitation[]>(this.baseUrl);
  }

  invite(email: string): Observable<AdminInvitation> {
    return this.http.post<AdminInvitation>(this.baseUrl, { email });
  }

  resend(id: string): Observable<AdminInvitation> {
    return this.http.post<AdminInvitation>(`${this.baseUrl}/${id}/resend`, {});
  }

  revoke(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
