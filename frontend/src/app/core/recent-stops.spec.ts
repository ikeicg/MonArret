import { TestBed } from '@angular/core/testing';

import { RecentStop, RecentStops } from './recent-stops';

const stop = (stopId: string, directionId = 0, routeId = '24'): RecentStop => ({
  routeId,
  routeName: 'Sherbrooke',
  directionId,
  directionName: directionId === 0 ? 'Est' : 'Ouest',
  stopId,
  stopName: `Stop ${stopId}`,
});

describe('RecentStops', () => {
  beforeEach(() => localStorage.clear());

  const create = () => {
    TestBed.resetTestingModule();
    return TestBed.inject(RecentStops);
  };

  it('keeps the newest first and does not repeat a route, direction and stop', () => {
    const recent = create();
    recent.add(stop('a'));
    recent.add(stop('b'));
    recent.add(stop('a'));

    expect(recent.list().map((s) => s.stopId)).toEqual(['a', 'b']);
  });

  it('treats the same stop in the other direction as a different entry', () => {
    const recent = create();
    recent.add(stop('a', 0));
    recent.add(stop('a', 1));

    expect(recent.list()).toHaveLength(2);
  });

  it('keeps the three most recent stops and discards the oldest', () => {
    const recent = create();
    for (let i = 0; i < 5; i++) recent.add(stop(`s${i}`));

    expect(recent.list().map((s) => s.stopId)).toEqual(['s4', 's3', 's2']);
  });

  it('survives a reload and never expires', () => {
    create().add(stop('a'));

    expect(create().list().map((s) => s.stopId)).toEqual(['a']);
  });

  it('skips stored entries that are not whole, and unreadable data', () => {
    localStorage.setItem('csm.recent-stops', JSON.stringify([stop('ok'), { routeId: '24' }]));
    expect(create().list().map((s) => s.stopId)).toEqual(['ok']);

    localStorage.setItem('csm.recent-stops', '{not json');
    expect(create().list()).toEqual([]);
  });
});
