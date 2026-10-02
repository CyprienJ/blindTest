import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AppLocale, currentLocale, switchLocale } from '../../core/i18n/locale';

/** FR / EN switch; changing language reloads the page in the chosen language. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-language-switch',
  standalone: true,
  template: `
    <div class="language-switch" role="group" i18n-aria-label aria-label="Language">
      <button
        type="button"
        lang="fr"
        [attr.aria-pressed]="locale === 'fr'"
        [class.selected]="locale === 'fr'"
        (click)="select('fr')"
        title="Français"
      >
        FR
      </button>
      <button
        type="button"
        lang="en"
        [attr.aria-pressed]="locale === 'en'"
        [class.selected]="locale === 'en'"
        (click)="select('en')"
        title="English"
      >
        EN
      </button>
    </div>
  `,
  styles: `
    .language-switch {
      display: inline-flex;
      padding: 2px;
      border: 1px solid var(--line);
      border-radius: 999px;
    }
    button {
      height: 26px;
      padding: 0 9px;
      border: 0;
      border-radius: 999px;
      background: transparent;
      color: var(--fg-2);
      font: inherit;
      font-size: 11px;
      font-weight: 700;
      cursor: pointer;
    }
    button.selected {
      background: var(--accent);
      color: var(--accent-ink);
    }
    button:focus-visible {
      outline: 2px solid var(--accent);
      outline-offset: 2px;
    }
  `,
})
export class LanguageSwitchComponent {
  readonly locale = currentLocale();

  select(locale: AppLocale): void {
    switchLocale(locale);
  }
}
