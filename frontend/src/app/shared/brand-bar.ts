import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LANGUAGES, Language, LanguageService } from '../core/language';
import { Logo } from './logo';

@Component({
  selector: 'app-brand-bar',
  imports: [RouterLink, TranslocoPipe, Logo],
  template: `
    <div class="brand-bar">
      <a class="brand" routerLink="/">
        <app-logo />
        <span class="brand-name">{{ 'app.title' | transloco }}</span>
      </a>
      <div class="lang-toggle" role="group" [attr.aria-label]="'lang.label' | transloco">
        @for (lang of languages; track lang) {
          <button
            type="button"
            [class.active]="language.current === lang"
            [attr.aria-pressed]="language.current === lang"
            (click)="choose(lang)"
          >
            {{ lang.toUpperCase() }}
          </button>
        }
      </div>
    </div>
  `,
})
export class BrandBar {
  protected readonly languages = LANGUAGES;
  protected readonly language = inject(LanguageService);

  protected choose(lang: Language): void {
    this.language.set(lang);
  }
}
