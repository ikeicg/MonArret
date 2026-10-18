import { TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader } from '@jsverse/transloco';
import { of } from 'rxjs';

import en from '../../../public/i18n/en.json';
import fr from '../../../public/i18n/fr.json';
import { SiteFooter } from './site-footer';

class JsonLoader implements TranslocoLoader {
  getTranslation(lang: string) {
    return of(lang === 'fr' ? fr : en);
  }
}

describe('SiteFooter', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SiteFooter],
      providers: [provideTransloco({ config: { availableLangs: ['en', 'fr'], defaultLang: 'en' }, loader: JsonLoader })],
    }).compileComponents();
  });

  it('credits the STM under CC BY 4.0 and disclaims affiliation', async () => {
    const fixture = TestBed.createComponent(SiteFooter);
    await fixture.whenStable();
    const root: HTMLElement = fixture.nativeElement;

    const links = Array.from(root.querySelectorAll('a'));
    expect(links.map((a) => a.textContent?.trim())).toEqual(['Société de transport de Montréal (STM)', 'CC BY 4.0']);
    expect(links[0].getAttribute('href')).toContain('stm.info');
    expect(links[1].getAttribute('href')).toContain('creativecommons.org/licenses/by/4.0');
    expect(root.textContent).toContain('not affiliated with or endorsed by the STM');
  });
});
