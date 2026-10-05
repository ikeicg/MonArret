import { BehaviorSubject, Observable, of, throwError } from 'rxjs';

import { PollResult, poll } from './poll';

describe('poll', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('requests at once, then again after each interval', () => {
    const request = vi.fn(() => of('page'));
    const results: PollResult<string>[] = [];

    const sub = poll(request, of(true), 1000, 0).subscribe((r) => results.push(r));
    expect(results).toEqual([{ ok: true, value: 'page' }]);

    vi.advanceTimersByTime(1000);
    expect(request).toHaveBeenCalledTimes(2);

    sub.unsubscribe();
    vi.advanceTimersByTime(5000);
    expect(request).toHaveBeenCalledTimes(2);
  });

  it('turns a failed request into a result and keeps polling', () => {
    const request = vi
      .fn<() => Observable<string>>()
      .mockReturnValueOnce(throwError(() => new Error('down')))
      .mockReturnValue(of('page'));
    const results: PollResult<string>[] = [];

    const sub = poll(request, of(true), 1000, 0).subscribe((r) => results.push(r));
    vi.advanceTimersByTime(1000);

    expect(results).toEqual([{ ok: false }, { ok: true, value: 'page' }]);
    sub.unsubscribe();
  });

  it('stops while the tab is hidden and resumes with an immediate request', () => {
    const visible = new BehaviorSubject(true);
    const request = vi.fn(() => of('page'));

    const sub = poll(request, visible, 1000, 0).subscribe();
    expect(request).toHaveBeenCalledTimes(1);

    visible.next(false);
    vi.advanceTimersByTime(10_000);
    expect(request).toHaveBeenCalledTimes(1);

    visible.next(true);
    expect(request).toHaveBeenCalledTimes(2);
    sub.unsubscribe();
  });
});
