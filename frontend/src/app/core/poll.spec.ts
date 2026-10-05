import { BehaviorSubject, Observable, of, throwError } from 'rxjs';

import { PollResult, poll } from './poll';

const options = { intervalMs: 1000, jitterMs: 0, maxBackoffMs: 4000 };

describe('poll', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('requests at once, then again after each interval', () => {
    const request = vi.fn(() => of('page'));
    const results: PollResult<string>[] = [];

    const sub = poll(request, { ...options, visible$: of(true), online$: of(true) }).subscribe((r) => results.push(r));
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

    const sub = poll(request, { ...options, visible$: of(true), online$: of(true) }).subscribe((r) => results.push(r));
    vi.advanceTimersByTime(2000);

    expect(results).toEqual([{ ok: false, retryInMs: 2000 }, { ok: true, value: 'page' }]);
    sub.unsubscribe();
  });

  it('doubles the wait after each failure up to the ceiling, and a success resets it', () => {
    const request = vi
      .fn<() => Observable<string>>()
      .mockReturnValueOnce(throwError(() => new Error('down')))
      .mockReturnValueOnce(throwError(() => new Error('down')))
      .mockReturnValueOnce(throwError(() => new Error('down')))
      .mockReturnValueOnce(throwError(() => new Error('down')))
      .mockReturnValue(of('page'));
    const results: PollResult<string>[] = [];

    const sub = poll(request, { ...options, visible$: of(true), online$: of(true) }).subscribe((r) => results.push(r));
    vi.advanceTimersByTime(2000 + 4000 + 4000);
    expect(results).toEqual([
      { ok: false, retryInMs: 2000 },
      { ok: false, retryInMs: 4000 },
      { ok: false, retryInMs: 4000 },
      { ok: false, retryInMs: 4000 },
    ]);

    vi.advanceTimersByTime(4000);
    expect(results.at(-1)).toEqual({ ok: true, value: 'page' });

    vi.advanceTimersByTime(1000);
    expect(request).toHaveBeenCalledTimes(6);
    sub.unsubscribe();
  });

  it('stops while the tab is hidden and resumes with an immediate request', () => {
    const visible = new BehaviorSubject(true);
    const request = vi.fn(() => of('page'));

    const sub = poll(request, { ...options, visible$: visible, online$: of(true) }).subscribe();
    expect(request).toHaveBeenCalledTimes(1);

    visible.next(false);
    vi.advanceTimersByTime(10_000);
    expect(request).toHaveBeenCalledTimes(1);

    visible.next(true);
    expect(request).toHaveBeenCalledTimes(2);
    sub.unsubscribe();
  });

  it('pauses while offline and resumes with an immediate request when back online', () => {
    const online = new BehaviorSubject(true);
    const request = vi.fn(() => of('page'));

    const sub = poll(request, { ...options, visible$: of(true), online$: online }).subscribe();
    online.next(false);
    vi.advanceTimersByTime(10_000);
    expect(request).toHaveBeenCalledTimes(1);

    online.next(true);
    expect(request).toHaveBeenCalledTimes(2);
    sub.unsubscribe();
  });

  it('ends for good once a result satisfies stopWhen, even across a hide and show', () => {
    const visible = new BehaviorSubject(true);
    const request = vi.fn(() => of('finished'));
    let completed = false;

    poll(request, { ...options, visible$: visible, online$: of(true), stopWhen: (v) => v === 'finished' }).subscribe({
      complete: () => (completed = true),
    });
    expect(completed).toBe(true);

    visible.next(false);
    visible.next(true);
    vi.advanceTimersByTime(5000);
    expect(request).toHaveBeenCalledTimes(1);
  });
});
