import { Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/** Star picker for a track difficulty from 1 (easiest) to 5 (hardest). */
@Component({
  selector: 'app-difficulty-rating',
  standalone: true,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DifficultyRatingComponent),
      multi: true,
    },
  ],
  template: `
    <div class="stars" role="radiogroup" [attr.aria-labelledby]="labelledBy()">
      @for (level of levels; track level) {
        <button
          type="button"
          role="radio"
          [class.filled]="level <= (value() ?? 0)"
          [attr.aria-checked]="value() === level"
          [attr.aria-label]="level + (level === 1 ? ' star' : ' stars')"
          [disabled]="disabled()"
          (click)="select(level)"
        >
          ★
        </button>
      }
      <span>1 = easiest · 5 = hardest</span>
    </div>
  `,
  styles: `
    .stars {
      display: flex;
      align-items: center;
    }
    button {
      border: 0;
      color: var(--line);
      background: none;
      font-size: 24px;
      cursor: pointer;
    }
    button.filled {
      color: var(--accent);
    }
    button:focus-visible {
      outline: 2px solid var(--accent);
      border-radius: 4px;
    }
    span {
      margin-left: 10px;
      color: var(--fg-2);
      font-size: 11px;
    }
  `,
})
export class DifficultyRatingComponent implements ControlValueAccessor {
  readonly labelledBy = input<string | null>(null);
  readonly levels = [1, 2, 3, 4, 5];
  readonly value = signal<number | null>(null);
  readonly disabled = signal(false);

  private onChange: (value: number | null) => void = () => {};
  private onTouched: () => void = () => {};

  select(level: number): void {
    this.value.set(level);
    this.onChange(level);
    this.onTouched();
  }

  writeValue(value: number | null): void {
    this.value.set(value);
  }

  registerOnChange(fn: (value: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }
}
