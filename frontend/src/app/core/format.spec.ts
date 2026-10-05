import { crowdingLevel, describeDelay, formatInstant, formatLocalDeparture, minutesFromSeconds } from './format';

describe('format', () => {
  it('shows a local departure as hours and minutes', () => {
    expect(formatLocalDeparture('2026-10-04T18:36:00')).toBe('18:36');
  });

  it('shows an instant in Montreal time', () => {
    // 22:36 UTC is 18:36 in Montreal in October (EDT).
    expect(formatInstant('2026-10-04T22:36:07Z', 'en-CA')).toMatch(/6:36/);
  });

  it('rounds a delay to whole minutes, at least one', () => {
    expect(minutesFromSeconds(213)).toBe(4);
    expect(minutesFromSeconds(-400)).toBe(7);
    expect(minutesFromSeconds(20)).toBe(1);
  });

  it('describes a delay, treating under half a minute as on time', () => {
    expect(describeDelay(10)).toEqual({ kind: 'onTime', minutes: 0 });
    expect(describeDelay(-25)).toEqual({ kind: 'onTime', minutes: 0 });
    expect(describeDelay(213)).toEqual({ kind: 'late', minutes: 4 });
    expect(describeDelay(-180)).toEqual({ kind: 'early', minutes: 3 });
  });

  it('reduces the feed occupancy values to three levels', () => {
    expect(crowdingLevel('MANY_SEATS_AVAILABLE')).toBe(1);
    expect(crowdingLevel('FEW_SEATS_AVAILABLE')).toBe(2);
    expect(crowdingLevel('FULL')).toBe(3);
  });

  it('hides the level when the feed gave no usable value', () => {
    expect(crowdingLevel('UNKNOWN')).toBe(0);
    expect(crowdingLevel('NO_DATA_AVAILABLE')).toBe(0);
    expect(crowdingLevel('NOT_BOARDABLE')).toBe(0);
  });
});
