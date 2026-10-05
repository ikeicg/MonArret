import {
  EMPTY,
  Observable,
  catchError,
  combineLatest,
  defer,
  distinctUntilChanged,
  fromEvent,
  map,
  merge,
  of,
  repeat,
  startWith,
  switchMap,
  takeWhile,
  timer,
} from 'rxjs';

// A failed request carries how long until the next attempt.
export type PollResult<T> = { ok: true; value: T } | { ok: false; retryInMs: number };

export const POLL_INTERVAL_MS = 20_000;
export const POLL_JITTER_MS = 3_000;
export const POLL_MAX_BACKOFF_MS = 120_000;

export function tabVisible$(): Observable<boolean> {
  return fromEvent(document, 'visibilitychange').pipe(
    map(() => !document.hidden),
    startWith(!document.hidden),
    distinctUntilChanged(),
  );
}

export function online$(): Observable<boolean> {
  return merge(fromEvent(window, 'online').pipe(map(() => true)), fromEvent(window, 'offline').pipe(map(() => false))).pipe(
    startWith(navigator.onLine),
    distinctUntilChanged(),
  );
}

export interface PollOptions<T> {
  visible$?: Observable<boolean>;
  online$?: Observable<boolean>;
  intervalMs?: number;
  jitterMs?: number;
  maxBackoffMs?: number;
  // Polling ends for good after a result that satisfies this.
  stopWhen?: (value: T) => boolean;
}

// Requests immediately, then again after the interval plus a random jitter. Each failed request doubles the
// wait, up to a ceiling; a success resets it. A failure becomes a result, not an error, so polling goes on.
// Nothing runs while the tab is hidden or the browser is offline; polling resumes with an immediate request.
export function poll<T>(request: () => Observable<T>, options: PollOptions<T> = {}): Observable<PollResult<T>> {
  const {
    visible$ = tabVisible$(),
    online$: online = online$(),
    intervalMs = POLL_INTERVAL_MS,
    jitterMs = POLL_JITTER_MS,
    maxBackoffMs = POLL_MAX_BACKOFF_MS,
    stopWhen,
  } = options;

  const wait = (baseMs: number) => baseMs + Math.random() * jitterMs;

  const cycle$ = defer(() => {
    let failures = 0;
    let nextDelayMs = 0;
    return defer(() => request()).pipe(
      map((value): PollResult<T> => {
        failures = 0;
        nextDelayMs = wait(intervalMs);
        return { ok: true, value };
      }),
      catchError(() => {
        failures++;
        nextDelayMs = wait(Math.min(intervalMs * 2 ** failures, maxBackoffMs));
        return of<PollResult<T>>({ ok: false, retryInMs: nextDelayMs });
      }),
      repeat({ delay: () => timer(nextDelayMs) }),
    );
  });

  const active$ = combineLatest([visible$, online]).pipe(
    map(([visible, isOnline]) => visible && isOnline),
    distinctUntilChanged(),
  );

  return active$.pipe(
    switchMap((active) => (active ? cycle$ : EMPTY)),
    takeWhile((result) => !(stopWhen && result.ok && stopWhen(result.value)), true),
  );
}
