import { EMPTY, Observable, catchError, defer, distinctUntilChanged, fromEvent, map, of, repeat, startWith, switchMap, timer } from 'rxjs';

export type PollResult<T> = { ok: true; value: T } | { ok: false };

export const POLL_INTERVAL_MS = 20_000;
export const POLL_JITTER_MS = 3_000;

export function tabVisible$(): Observable<boolean> {
  return fromEvent(document, 'visibilitychange').pipe(
    map(() => !document.hidden),
    startWith(!document.hidden),
    distinctUntilChanged(),
  );
}

// Requests immediately, then again after the interval plus a random jitter. A failed request becomes a
// result, not an error, so the polling keeps going. Nothing runs while the tab is hidden; polling resumes
// with an immediate request when it is shown again.
export function poll<T>(
  request: () => Observable<T>,
  visible$: Observable<boolean> = tabVisible$(),
  intervalMs = POLL_INTERVAL_MS,
  jitterMs = POLL_JITTER_MS,
): Observable<PollResult<T>> {
  const cycle$ = defer(() => request()).pipe(
    map((value): PollResult<T> => ({ ok: true, value })),
    catchError(() => of<PollResult<T>>({ ok: false })),
    repeat({ delay: () => timer(intervalMs + Math.random() * jitterMs) }),
  );
  return visible$.pipe(switchMap((visible) => (visible ? cycle$ : EMPTY)));
}
