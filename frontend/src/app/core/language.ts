import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Translation, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { Observable } from 'rxjs';

export const LANGUAGES = ['en', 'fr'] as const;
export type Language = (typeof LANGUAGES)[number];

const STORAGE_KEY = 'csm.lang';

@Injectable({ providedIn: 'root' })
export class TranslocoHttpLoader implements TranslocoLoader {
  private readonly http = inject(HttpClient);

  getTranslation(lang: string): Observable<Translation> {
    return this.http.get<Translation>(`/i18n/${lang}.json`);
  }
}

// The saved choice wins; otherwise the browser's language, English by default.
export function initialLanguage(): Language {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'en' || saved === 'fr') return saved;
  } catch {
    // storage unavailable
  }
  return navigator.language?.toLowerCase().startsWith('fr') ? 'fr' : 'en';
}

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly transloco = inject(TranslocoService);

  private readonly active = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  // Reads a signal, so templates and computeds that use it follow language changes.
  get current(): Language {
    return this.active() as Language;
  }

  set(lang: Language): void {
    this.transloco.setActiveLang(lang);
    document.documentElement.lang = lang;
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // storage unavailable
    }
  }
}
