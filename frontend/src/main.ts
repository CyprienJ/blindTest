import { currentLocale } from './app/core/i18n/locale';
import { bootstrap } from './bootstrap';

// French translations are registered beforehand by the `src/translations.ts` polyfill.
bootstrap(currentLocale()).catch((err) => console.error(err));
