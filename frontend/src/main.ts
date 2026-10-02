import { loadTranslations } from '@angular/localize';
import { currentLocale } from './app/core/i18n/locale';

// Translations must be registered before any module that evaluates `$localize` is loaded, so the
// application itself is imported dynamically once they are in place.
async function start(): Promise<void> {
  const locale = currentLocale();
  document.documentElement.lang = locale;
  if (locale === 'fr') {
    try {
      const response = await fetch('/i18n/fr.json', { cache: 'no-cache' });
      const { translations } = (await response.json()) as {
        translations: Record<string, string>;
      };
      loadTranslations(translations);
    } catch (error) {
      // Untranslated text falls back to the English source rather than blocking the app.
      console.error('French translations could not be loaded', error);
    }
  }
  const { bootstrap } = await import('./bootstrap');
  await bootstrap(locale);
}

start().catch((err) => console.error(err));
