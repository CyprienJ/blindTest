import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  NgZone,
  OnDestroy,
  OnInit,
  QueryList,
  ViewChildren,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { TURNSTILE_SITE_KEY } from '../../../core/config';
import {
  GUEST_NICKNAME_MAX_LENGTH,
  GUEST_NICKNAME_MIN_LENGTH,
  LOBBY_CODE_ALPHABET,
  LOBBY_CODE_LENGTH,
} from '../../../core/models/lobby.model';
import { AuthService } from '../../../core/services/auth.service';
import { GuestSessionService } from '../../../core/services/guest-session.service';
import { LobbyService } from '../../../core/services/lobby.service';

declare const turnstile: {
  render: (container: string | HTMLElement, options: Record<string, unknown>) => string;
  reset: (widgetId: string) => void;
  remove: (widgetId: string) => void;
};

/**
 * Join a multiplayer lobby with the six-character code its host shared (MP-05). Visitors without
 * an account join as guests with a nickname and a Turnstile check (MP-09).
 */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-join-session',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './join-session.component.html',
  styleUrl: './join-session.component.scss',
})
export class JoinSessionComponent implements OnInit, OnDestroy {
  private readonly lobbies = inject(LobbyService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly guestSession = inject(GuestSessionService);
  private readonly zone = inject(NgZone);

  @ViewChildren('cell') private readonly cells!: QueryList<ElementRef<HTMLInputElement>>;

  readonly positions = Array.from({ length: LOBBY_CODE_LENGTH }, (_, index) => index);
  characters: string[] = Array.from({ length: LOBBY_CODE_LENGTH }, () => '');
  joining = false;
  errorMessage = '';
  /** Without an account, the visitor joins as a guest with a nickname. */
  readonly guest = !this.auth.isLoggedIn();
  readonly nicknameMaxLength = GUEST_NICKNAME_MAX_LENGTH;
  nickname = '';
  turnstileToken = '';
  private widgetId = '';
  private destroyed = false;

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('code');
    if (code) {
      this.fill(0, code);
    }
    if (this.guest) {
      setTimeout(() => this.initTurnstile(), 0);
    }
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    if (this.widgetId && typeof turnstile !== 'undefined') {
      turnstile.remove(this.widgetId);
    }
  }

  get nicknameValid(): boolean {
    const length = [...this.nickname.trim()].length;
    return length >= GUEST_NICKNAME_MIN_LENGTH && length <= GUEST_NICKNAME_MAX_LENGTH;
  }

  /** Whether every field the join needs is filled in. */
  get ready(): boolean {
    return this.missing === 0 && (!this.guest || (this.nicknameValid && !!this.turnstileToken));
  }

  onNicknameInput(event: Event): void {
    this.nickname = (event.target as HTMLInputElement).value;
    this.errorMessage = '';
  }

  get code(): string {
    return this.characters.join('');
  }

  get missing(): number {
    return this.characters.filter((character) => !character).length;
  }

  cellLabel(index: number): string {
    return $localize`Code character ${index + 1}:position: of ${this.positions.length}:total:`;
  }

  onInput(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const previous = this.characters[index];
    // Typing into a filled cell appends to its character; keep only what was typed.
    const typed =
      previous && input.value.startsWith(previous)
        ? input.value.slice(previous.length) || previous
        : input.value;
    input.value = previous;
    this.fill(index, typed);
  }

  onKeydown(index: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace' && !this.characters[index] && index > 0) {
      this.characters[index - 1] = '';
      this.focus(index - 1);
      event.preventDefault();
    } else if (event.key === 'ArrowLeft' && index > 0) {
      this.focus(index - 1);
    } else if (event.key === 'ArrowRight' && index < LOBBY_CODE_LENGTH - 1) {
      this.focus(index + 1);
    } else if (event.key === 'Enter') {
      this.join();
    }
  }

  onPaste(index: number, event: ClipboardEvent): void {
    event.preventDefault();
    this.fill(index, event.clipboardData?.getData('text') ?? '');
  }

  join(): void {
    if (!this.ready || this.joining) {
      return;
    }
    if (this.guest) {
      this.joinAsGuest();
      return;
    }
    const code = this.code;
    this.joining = true;
    this.errorMessage = '';
    this.lobbies
      .join(code)
      .pipe(finalize(() => (this.joining = false)))
      .subscribe({
        next: (lobby) => void this.router.navigate(['/lobby', lobby.code]),
        error: (error) => (this.errorMessage = this.describe(error)),
      });
  }

  private joinAsGuest(): void {
    const turnstileToken = this.turnstileToken;
    this.turnstileToken = '';
    this.joining = true;
    this.errorMessage = '';
    this.lobbies
      .joinAsGuest(this.code, this.nickname.trim(), turnstileToken)
      .pipe(finalize(() => (this.joining = false)))
      .subscribe({
        next: ({ guestToken, lobby }) => {
          const me = lobby.participants.find(
            (participant) => participant.participantId === lobby.participantId,
          );
          this.guestSession.set({
            token: guestToken,
            code: lobby.code,
            sessionId: lobby.sessionId,
            participantId: lobby.participantId,
            nickname: me?.displayName ?? this.nickname.trim(),
          });
          void this.router.navigate(['/lobby', lobby.code]);
        },
        error: (error) => {
          // A Turnstile token is single-use, so every failed attempt needs a fresh check.
          this.resetTurnstile();
          this.errorMessage = this.describe(error);
        },
      });
  }

  private initTurnstile(): void {
    if (this.destroyed) {
      return;
    }
    if (typeof turnstile === 'undefined') {
      setTimeout(() => this.initTurnstile(), 500);
      return;
    }
    this.widgetId = turnstile.render('#guest-turnstile', {
      sitekey: TURNSTILE_SITE_KEY,
      theme: 'dark',
      action: 'guest-join',
      'response-field': false,
      'refresh-expired': 'auto',
      callback: (token: string) => this.zone.run(() => (this.turnstileToken = token)),
      'expired-callback': () => this.zone.run(() => (this.turnstileToken = '')),
      'error-callback': (): boolean => {
        this.zone.run(() => {
          this.turnstileToken = '';
          this.errorMessage = $localize`Security check could not be completed. Please try again.`;
        });
        return true;
      },
    });
  }

  private resetTurnstile(): void {
    this.turnstileToken = '';
    if (this.widgetId && typeof turnstile !== 'undefined') {
      turnstile.reset(this.widgetId);
    }
  }

  /** Writes accepted characters from `index` on, ignoring anything outside the code alphabet. */
  private fill(index: number, raw: string): void {
    this.errorMessage = '';
    const accepted = raw
      .toUpperCase()
      .split('')
      .filter((character) => LOBBY_CODE_ALPHABET.includes(character));
    if (raw && accepted.length === 0) {
      this.errorMessage = $localize`Codes use letters and the digits 2 to 9 only.`;
      return;
    }
    let position = index;
    for (const character of accepted) {
      if (position >= LOBBY_CODE_LENGTH) {
        break;
      }
      this.characters[position] = character;
      position++;
    }
    if (!raw) {
      this.characters[index] = '';
    }
    this.focus(Math.min(position, LOBBY_CODE_LENGTH - 1));
  }

  private focus(index: number): void {
    queueMicrotask(() => this.cells?.get(index)?.nativeElement.focus());
  }

  private describe(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.error?.code) {
        case 'LOBBY_NOT_FOUND':
          return $localize`No open lobby uses this code. Check it with your host — the game may have already started.`;
        case 'LOBBY_FULL':
          return $localize`This lobby is full (10 players).`;
        case 'EMAIL_NOT_VERIFIED':
          return $localize`Verify your email address before joining a game.`;
        case 'GUEST_NICKNAME_INVALID':
          return $localize`Choose a nickname of 2 to 20 characters.`;
        case 'GUEST_NICKNAME_TAKEN':
          return $localize`Someone in this lobby already uses this nickname. Pick another one.`;
        case 'GUEST_JOIN_RATE_LIMITED':
          return $localize`Too many attempts to join. Wait a few minutes and try again.`;
        case 'TURNSTILE_FAILED':
          return $localize`Security check failed. Please try again.`;
        default:
          break;
      }
      if (error.status === 0) {
        return $localize`The server can’t be reached. Check your connection and try again.`;
      }
    }
    return $localize`You could not join this lobby. Please try again.`;
  }
}
