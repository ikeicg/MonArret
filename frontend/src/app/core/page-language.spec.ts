import { readFileSync } from 'node:fs';

import { declarePageLanguage } from './language';

describe('page language', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.lang = 'en';
  });

  it('declares French from the start when French is the saved choice', () => {
    localStorage.setItem('monarret.lang', 'fr');

    declarePageLanguage();

    expect(document.documentElement.lang).toBe('fr');
  });

  it('declares English when nothing is saved and the browser is not French', () => {
    declarePageLanguage();

    expect(document.documentElement.lang).toBe('en');
  });

  it('tells browsers not to offer machine translation, because the app has its own languages', () => {
    const html = readFileSync('src/index.html', 'utf-8');

    expect(html).toContain('translate="no"');
    expect(html).toContain('<meta name="google" content="notranslate">');
  });
});
