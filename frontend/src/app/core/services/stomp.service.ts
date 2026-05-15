import { inject, Injectable } from '@angular/core';
import { RxStomp } from '@stomp/rx-stomp';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class StompService {
  private readonly auth = inject(AuthService);
  private readonly rxStomp = new RxStomp();

  connect(): void {
    const token = this.auth.getAccessToken();
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.rxStomp.configure({
      brokerURL: `${protocol}//${location.host}/ws`,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 3000,
    });
    this.rxStomp.activate();
  }

  disconnect(): void {
    this.rxStomp.deactivate();
  }

  watch<T>(destination: string): Observable<T> {
    return this.rxStomp.watch(destination).pipe(
      map((msg) => JSON.parse(msg.body) as T),
    );
  }
}
