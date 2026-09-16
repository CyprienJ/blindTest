import { inject, Injectable, InjectionToken, OnDestroy } from '@angular/core';
import { RxStomp, RxStompState } from '@stomp/rx-stomp';
import { IFrame, ReconnectionTimeMode } from '@stomp/stompjs';
import { BehaviorSubject, filter, map, Observable, Subject, Subscription } from 'rxjs';
import { AuthService } from './auth.service';
import { ActiveSoloSession } from '../models/game-session.model';
import {
  CurrentSnapshotPayload,
  GAME_PROTOCOL_VERSION,
  PrivateSessionEvent,
  PublicSessionEvent,
  SessionCommand,
  SessionCommandType,
} from '../models/game-protocol.model';

export type GameSocketStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting';

/** Factory for the underlying STOMP client — overridable in tests. */
export const GAME_SOCKET_CLIENT = new InjectionToken<() => RxStomp>('GAME_SOCKET_CLIENT', {
  providedIn: 'root',
  factory: () => () => new RxStomp(),
});

const BASE_RECONNECT_DELAY_MS = 2000;
const MAX_RECONNECT_DELAY_MS = 30000;

/**
 * `crypto.randomUUID()` only exists in secure contexts (HTTPS, or http://localhost);
 * `crypto.getRandomValues()` has no such restriction, so build a v4 UUID from it instead. This
 * keeps command IDs working when the app is served over plain HTTP on a non-localhost origin,
 * such as an internal Docker network.
 */
function randomCommandId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * Owns the single gameplay STOMP connection: access-token connect headers,
 * a typed command API, exponential reconnect, automatic resubscription and
 * post-reconnect snapshot recovery, and teardown on route exit or logout.
 */
@Injectable({ providedIn: 'root' })
export class GameSocketService implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly createClient = inject(GAME_SOCKET_CLIENT);

  private client: RxStomp | null = null;
  private sessionId: string | null = null;
  private commandDestination = '';
  private sessionVersion = 0;
  private everConnected = false;
  private clientSubscriptions = new Subscription();

  private readonly statusSubject = new BehaviorSubject<GameSocketStatus>('idle');
  private readonly publicSubject = new Subject<PublicSessionEvent>();
  private readonly privateSubject = new Subject<PrivateSessionEvent>();
  private readonly fatalErrorSubject = new Subject<string>();
  private readonly logoutSubscription: Subscription;

  readonly status$ = this.statusSubject.asObservable();
  readonly publicEvents$ = this.publicSubject.asObservable();
  readonly privateEvents$ = this.privateSubject.asObservable();
  /**
   * A STOMP ERROR frame closes the connection it arrived on (per the STOMP spec), and the
   * server sends one whenever this session's destinations are rejected — most commonly because
   * the session no longer exists (expired, aborted, or lost to an in-memory backend restart).
   * That rejection is stable: reconnecting and resubscribing to the same destinations fails the
   * same way every time, so retrying forever would only spin the exponential-backoff loop
   * without ever recovering. This emits once so the caller can show a terminal error instead.
   */
  readonly fatalError$ = this.fatalErrorSubject.asObservable();
  readonly snapshots$: Observable<CurrentSnapshotPayload> = this.privateSubject.pipe(
    filter((event) => event.type === 'CURRENT_SNAPSHOT'),
    map((event) => event.payload as CurrentSnapshotPayload),
  );

  constructor() {
    this.logoutSubscription = this.auth.currentUser$.subscribe((user) => {
      if (!user) {
        this.disconnect();
      }
    });
  }

  /** Current optimistic session version (last authoritative version seen). */
  get version(): number {
    return this.sessionVersion;
  }

  /** Opens the connection for a session. A repeat call for the same session is a no-op. */
  connect(session: ActiveSoloSession): void {
    if (this.sessionId === session.sessionId && this.client) {
      return;
    }
    this.disconnect();

    this.sessionId = session.sessionId;
    this.commandDestination = session.connection.commandDestination;
    this.sessionVersion = session.version;
    this.everConnected = false;

    const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const client = this.createClient();
    client.configure({
      brokerURL: `${scheme}//${window.location.host}${session.connection.webSocketEndpoint}`,
      connectHeaders: this.authHeaders(),
      beforeConnect: (instance: RxStomp) => {
        instance.stompClient.connectHeaders = this.authHeaders();
      },
      reconnectDelay: BASE_RECONNECT_DELAY_MS,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      maxReconnectDelay: MAX_RECONNECT_DELAY_MS,
      connectionTimeout: 8000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    this.clientSubscriptions = new Subscription();
    this.clientSubscriptions.add(
      client.connectionState$.subscribe((state) => this.onConnectionState(state)),
    );
    this.clientSubscriptions.add(
      client.stompErrors$.subscribe((frame) => this.onStompError(frame)),
    );
    this.clientSubscriptions.add(
      client
        .watch(session.connection.eventDestination)
        .subscribe((message) => this.emitPublic(message.body)),
    );
    this.clientSubscriptions.add(
      client
        .watch(session.connection.privateDestination)
        .subscribe((message) => this.emitPrivate(message.body)),
    );

    this.client = client;
    client.activate();
  }

  /** Closes the connection and resets all connection state. */
  disconnect(): void {
    this.clientSubscriptions.unsubscribe();
    this.clientSubscriptions = new Subscription();
    if (this.client) {
      void this.client.deactivate({ force: true });
      this.client = null;
    }
    this.sessionId = null;
    this.commandDestination = '';
    this.sessionVersion = 0;
    this.everConnected = false;
    if (this.statusSubject.value !== 'idle') {
      this.statusSubject.next('idle');
    }
  }

  startGame(): void {
    this.send('START_GAME');
  }

  submitAnswer(answer: string): void {
    this.send('SUBMIT_ANSWER', answer);
  }

  skipRound(): void {
    this.send('SKIP_ROUND');
  }

  advanceRound(): void {
    this.send('ADVANCE_ROUND');
  }

  requestSnapshot(): void {
    this.send('REQUEST_SNAPSHOT');
  }

  ngOnDestroy(): void {
    this.logoutSubscription.unsubscribe();
    this.disconnect();
  }

  private send(type: SessionCommandType, answer?: string): void {
    if (!this.client || !this.commandDestination) {
      return;
    }
    const command: SessionCommand = {
      protocolVersion: GAME_PROTOCOL_VERSION,
      commandId: randomCommandId(),
      expectedSessionVersion: this.sessionVersion,
      type,
    };
    if (type === 'SUBMIT_ANSWER') {
      command.answer = answer;
    }
    this.client.publish({
      destination: this.commandDestination,
      body: JSON.stringify(command),
      headers: { 'content-type': 'application/json' },
    });
  }

  private onConnectionState(state: RxStompState): void {
    if (state === RxStompState.OPEN) {
      this.statusSubject.next('connected');
      if (this.everConnected) {
        // A reconnection: watched destinations are re-subscribed by RxStomp; ask
        // the server to replace any state the client missed while offline.
        this.requestSnapshot();
      }
      this.everConnected = true;
      return;
    }
    if (state === RxStompState.CONNECTING) {
      this.statusSubject.next(this.everConnected ? 'reconnecting' : 'connecting');
      return;
    }
    if (state === RxStompState.CLOSED && this.client) {
      // RxStomp will retry automatically while the client stays active.
      this.statusSubject.next(this.everConnected ? 'reconnecting' : 'connecting');
    }
  }

  private onStompError(frame: IFrame): void {
    const message = frame.headers['message'] || 'This game session is no longer available.';
    this.disconnect();
    this.fatalErrorSubject.next(message);
  }

  private emitPublic(body: string): void {
    const event = JSON.parse(body) as PublicSessionEvent;
    this.sessionVersion = Math.max(this.sessionVersion, event.metadata.sessionVersion);
    this.publicSubject.next(event);
  }

  private emitPrivate(body: string): void {
    const event = JSON.parse(body) as PrivateSessionEvent;
    this.sessionVersion = Math.max(this.sessionVersion, event.metadata.sessionVersion);
    this.privateSubject.next(event);
  }

  private authHeaders(): { Authorization: string } {
    return { Authorization: `Bearer ${this.auth.getAccessToken() ?? ''}` };
  }
}
