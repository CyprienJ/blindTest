/**
 * Application language. French is the default; English is kept as the source language of every
 * template and `$localize` string. The choice is stored per browser and applied on load, because
 * translations are loaded once before the application starts.
 *
 * This module must not use `$localize`: `main.ts` imports it before translations are loaded.
 */
export type AppLocale = 'fr' | 'en';

export const DEFAULT_LOCALE: AppLocale = 'fr';
export const LOCALE_STORAGE_KEY = 'locale';
export const SUPPORTED_LOCALES: readonly AppLocale[] = ['fr', 'en'];

export function currentLocale(): AppLocale {
  try {
    const stored = localStorage.getItem(LOCALE_STORAGE_KEY);
    return stored === 'en' || stored === 'fr' ? stored : DEFAULT_LOCALE;
  } catch {
    return DEFAULT_LOCALE;
  }
}

/** Stores the language and reloads so every translated string is rendered again. */
export function switchLocale(locale: AppLocale): void {
  if (locale === currentLocale()) {
    return;
  }
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // Storage may be unavailable; the reload then keeps the default language.
  }
  window.location.reload();
}
