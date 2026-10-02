import { HttpInterceptorFn } from '@angular/common/http';
import { currentLocale } from '../i18n/locale';

/** Tells the backend which language to use, for example in the emails it sends. */
export const localeInterceptor: HttpInterceptorFn = (request, next) =>
  next(request.clone({ setHeaders: { 'Accept-Language': currentLocale() } }));
