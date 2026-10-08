import { loadTranslations } from '@angular/localize';
import { currentLocale } from './app/core/i18n/locale';
import fr from './locale/fr.json';

// Runs as a polyfill so translations are registered before any application module evaluates
// `$localize`. Bundling them keeps them in the hashed, preloaded initial files instead of a
// separate request that would delay startup.
const locale = currentLocale();
document.documentElement.lang = locale;
if (locale === 'fr') {
  loadTranslations(fr.translations);
}
