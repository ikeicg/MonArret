import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';

import { App } from './app';
import { TranslocoHttpLoader } from './core/language';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'fr'], defaultLang: 'en' },
          loader: TranslocoHttpLoader,
        }),
      ],
    }).compileComponents();
  });

  it('shows a button for each language', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll('.lang-toggle button');
    expect(Array.from(buttons).map((b) => b.textContent?.trim())).toEqual(['EN', 'FR']);
  });
});
