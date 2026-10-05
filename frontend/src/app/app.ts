import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LANGUAGES, Language, LanguageService } from './core/language';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, TranslocoPipe],
  templateUrl: './app.html',
})
export class App {
  protected readonly languages = LANGUAGES;
  protected readonly language = inject(LanguageService);

  protected choose(lang: Language): void {
    this.language.set(lang);
  }
}
