import en from '../../public/i18n/en.json';
import fr from '../../public/i18n/fr.json';

function keys(tree: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(tree).flatMap(([key, value]) =>
    typeof value === 'object' && value !== null
      ? keys(value as Record<string, unknown>, `${prefix}${key}.`)
      : [`${prefix}${key}`],
  );
}

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
});
