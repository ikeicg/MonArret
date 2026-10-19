import { provideHttpClient } from '@angular/common/http';
import { ApplicationConfig, isDevMode, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';

import { routes } from './app.routes';
import { LANGUAGES, TranslocoHttpLoader, declarePageLanguage, initialLanguage } from './core/language';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAppInitializer(declarePageLanguage),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(),
    provideTransloco({
      config: {
        availableLangs: [...LANGUAGES],
        defaultLang: initialLanguage(),
        fallbackLang: 'en',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader,
    }),
  ],
};
