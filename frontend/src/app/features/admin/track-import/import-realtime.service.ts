import { inject, Injectable, OnDestroy } from '@angular/core';
import { IMessage } from '@stomp/stompjs';
import { RxStomp, RxStompState } from '@stomp/rx-stomp';
import { BehaviorSubject, map, Observable, Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import {
  ImportJobStatus,
  ImportRealtimeEvent,
  RealtimeConnectionState,
} from './track-import.model';

@Injectable({ providedIn: 'root' })
export class ImportRealtimeService implements OnDestroy {
  private readonly auth = inject(AuthService);
  private client: RxStomp | null = null;
  private stateSubscription: Subscription | null = null;
  private readonly stateSubject = new BehaviorSubject<RealtimeConnectionState>('offline');
  readonly connectionState$ = this.stateSubject.asObservable();

  watch(jobId: string): Observable<ImportRealtimeEvent> {
    this.disconnect();
    const client = new RxStomp();
    const socketProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    client.configure({
      brokerURL: `${socketProtocol}//${window.location.host}/ws`,
      connectHeaders: {
        Authorization: `Bearer ${this.auth.getAccessToken() ?? ''}`,
      },
      connectionTimeout: 5000,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });
    this.stateSubscription = client.connectionState$.subscribe((state) => {
      this.stateSubject.next(
        state === RxStompState.OPEN
          ? 'connected'
          : state === RxStompState.CONNECTING
            ? 'connecting'
            : 'offline',
      );
    });
    const events = client
      .watch(`/user/queue/import/${jobId}`)
      .pipe(map((message) => parseImportRealtimeEvent(message)));
    this.client = client;
    client.activate();
    return events;
  }

  disconnect(): void {
    this.stateSubscription?.unsubscribe();
    this.stateSubscription = null;
    if (this.client) {
      void this.client.deactivate({ force: true });
      this.client = null;
    }
    this.stateSubject.next('offline');
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.stateSubject.complete();
  }
}

export function parseImportRealtimeEvent(message: Pick<IMessage, 'body'>): ImportRealtimeEvent {
  const payload = JSON.parse(message.body) as Record<string, unknown>;
  const jobId = String(payload['jobId']);
  if (typeof payload['trackId'] === 'string') {
    return { kind: 'done', jobId, trackId: payload['trackId'] };
  }
  if (typeof payload['errorMessage'] === 'string') {
    return { kind: 'failed', jobId, errorMessage: payload['errorMessage'] };
  }
  return {
    kind: 'progress',
    jobId,
    status: String(payload['status']) as ImportJobStatus,
    progress: Number(payload['progress']),
  };
}
