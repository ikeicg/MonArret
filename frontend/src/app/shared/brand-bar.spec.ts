import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';

import { TranslocoHttpLoader } from '../core/language';
import { BrandBar } from './brand-bar';

describe('BrandBar', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [BrandBar],
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

  it('shows a button for each language and marks the active one', async () => {
    const fixture = TestBed.createComponent(BrandBar);
    await fixture.whenStable();

    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.lang-toggle button'));
    expect(buttons.map((b) => b.textContent?.trim())).toEqual(['EN', 'FR']);
    expect(buttons.map((b) => b.getAttribute('aria-pressed'))).toEqual(['true', 'false']);
  });

  it('switches language and remembers the choice', async () => {
    const fixture = TestBed.createComponent(BrandBar);
    await fixture.whenStable();

    (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.lang-toggle button')[1].click();
    await fixture.whenStable();

    expect(localStorage.getItem('csm.lang')).toBe('fr');
    expect(document.documentElement.lang).toBe('fr');
  });
});
