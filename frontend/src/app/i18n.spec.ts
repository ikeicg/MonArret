import en from '../../public/i18n/en.json';
import fr from '../../public/i18n/fr.json';
import { readFileSync } from 'node:fs';

function keys(tree: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(tree).flatMap(([key, value]) =>
    typeof value === 'object' && value !== null
      ? keys(value as Record<string, unknown>, `${prefix}${key}.`)
      : [`${prefix}${key}`],
  );
}

const sources = ['src/app/pick/pick-page.html', 'src/app/trip/trip-page.html', 'src/app/shared/brand-bar.ts', 'src/app/shared/site-footer.ts']
  .map((file) => readFileSync(file, 'utf-8'))
  .join('\n');

describe('translations', () => {
  it('have the same keys in English and French', () => {
    expect(keys(fr).sort()).toEqual(keys(en).sort());
  });

  it('cover every headline status the backend can send', () => {
    const statuses = [
      'LIVE_DATA_UNAVAILABLE', 'CANCELLED', 'TRIP_FINISHED', 'SIGNAL_LOST', 'LIVE',
      'NOT_TRACKED_YET', 'STARTED_LATE', 'NO_LIVE_BUS_FOUND', 'NO_LIVE_INFO',
    ];
    for (const status of statuses) {
      expect(keys(en)).toContain(`headline.${status}`);
    }
  });

  it('have every key the templates use as a literal', () => {
    const used = [...sources.matchAll(/'([a-z]+\.[A-Za-z_]+)'\s*(?:\||\)|\?|:)/g)].map((m) => m[1]);
    const missing = used.filter((key) => !keys(en).includes(key));

    expect(used.length).toBeGreaterThan(20);
    expect(missing).toEqual([]);
  });

  it('have keys under every prefix the templates build dynamically', () => {
    const prefixes = [...sources.matchAll(/'([a-z]+)\.'\s*\+/g)].map((m) => m[1]);

    expect(prefixes.length).toBeGreaterThan(5);
    for (const prefix of new Set(prefixes)) {
      expect(keys(en).some((key) => key.startsWith(`${prefix}.`))).toBe(true);
    }
  });
});
