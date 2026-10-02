import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  OnInit,
  QueryList,
  ViewChildren,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { LOBBY_CODE_ALPHABET, LOBBY_CODE_LENGTH } from '../../../core/models/lobby.model';
import { LobbyService } from '../../../core/services/lobby.service';

/** Join a multiplayer lobby with the six-character code its host shared (MP-05). */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-join-session',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './join-session.component.html',
  styleUrl: './join-session.component.scss',
})
export class JoinSessionComponent implements OnInit {
  private readonly lobbies = inject(LobbyService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  @ViewChildren('cell') private readonly cells!: QueryList<ElementRef<HTMLInputElement>>;

  readonly positions = Array.from({ length: LOBBY_CODE_LENGTH }, (_, index) => index);
  characters: string[] = Array.from({ length: LOBBY_CODE_LENGTH }, () => '');
  joining = false;
  errorMessage = '';

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('code');
    if (code) {
      this.fill(0, code);
    }
  }

  get code(): string {
    return this.characters.join('');
  }

  get missing(): number {
    return this.characters.filter((character) => !character).length;
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
    if (this.missing > 0 || this.joining) {
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

  /** Writes accepted characters from `index` on, ignoring anything outside the code alphabet. */
  private fill(index: number, raw: string): void {
    this.errorMessage = '';
    const accepted = raw
      .toUpperCase()
      .split('')
      .filter((character) => LOBBY_CODE_ALPHABET.includes(character));
    if (raw && accepted.length === 0) {
      this.errorMessage = 'Codes use letters and the digits 2 to 9 only.';
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
          return 'No open lobby uses this code. Check it with your host — the game may have already started.';
        case 'LOBBY_FULL':
          return 'This lobby is full (10 players).';
        case 'EMAIL_NOT_VERIFIED':
          return 'Verify your email address before joining a game.';
        default:
          break;
      }
      if (error.status === 0) {
        return 'The server can’t be reached. Check your connection and try again.';
      }
    }
    return 'You could not join this lobby. Please try again.';
  }
}
