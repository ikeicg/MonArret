import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { interval, map, scan, startWith, switchMap } from 'rxjs';

import { ApiService } from '../core/api.service';
import { crowdingLevel, describeDelay, formatInstant, formatLocalDeparture, minutesFromSeconds } from '../core/format';
import { LanguageService } from '../core/language';
import { HeadlineStatus, TripHealth } from '../core/models';
import { POLL_INTERVAL_MS, online$, poll } from '../core/poll';
import { BrandBar } from '../shared/brand-bar';

interface TripState {
  health: TripHealth | null;
  // The latest refresh failed; health, if any, is from an earlier one.
  failed: boolean;
  refreshedAt: number | null;
  // When the next attempt after a failed refresh is due.
  retryAt: number | null;
}

const INITIAL: TripState = { health: null, failed: false, refreshedAt: null, retryAt: null };
const REFRESH_SECONDS = POLL_INTERVAL_MS / 1000;

@Component({
  selector: 'app-trip-page',
  imports: [RouterLink, TranslocoPipe, BrandBar],
  templateUrl: './trip-page.html',
})
export class TripPage {
  private readonly api = inject(ApiService);
  private readonly language = inject(LanguageService);

  // Bound from the route parameters.
  readonly tripId = input.required<string>();
  readonly stopSequence = input.required<string>();

  // Bound from the query string the picker adds; absent on a shared link.
  readonly route = input<string>();
  readonly routeName = input<string>();
  readonly stopName = input<string>();
  readonly departs = input<string>();

  protected readonly state = toSignal(
    toObservable(computed(() => ({ tripId: this.tripId(), stopSequence: Number(this.stopSequence()) }))).pipe(
      switchMap(({ tripId, stopSequence }) =>
        poll(() => this.api.tripHealth(tripId, stopSequence).pipe(map((response) => response.data)), {
          stopWhen: (health) => health?.headline.status === 'TRIP_FINISHED',
        }).pipe(
          scan((state, result): TripState => {
            if (result.ok && result.value) {
              return { health: result.value, failed: false, refreshedAt: Date.now(), retryAt: null };
            }
            return { ...state, failed: true, retryAt: result.ok ? null : Date.now() + result.retryInMs };
          }, INITIAL),
        ),
      ),
    ),
    { initialValue: INITIAL },
  );

  private readonly now = toSignal(interval(1000).pipe(startWith(0), map(() => Date.now())), {
    initialValue: Date.now(),
  });

  protected readonly online = toSignal(online$(), { initialValue: navigator.onLine });

  protected readonly finished = computed(() => this.state().health?.headline.status === 'TRIP_FINISHED');

  protected readonly secondsToRetry = computed(() => {
    const at = this.state().retryAt;
    return at === null ? null : Math.max(0, Math.ceil((at - this.now()) / 1000));
  });

  protected readonly secondsSinceRefresh = computed(() => {
    const at = this.state().refreshedAt;
    return at === null ? null : Math.max(0, Math.floor((this.now() - at) / 1000));
  });

  protected readonly secondsToRefresh = computed(() => {
    const since = this.secondsSinceRefresh();
    return since === null ? null : Math.max(0, REFRESH_SECONDS - since);
  });

  protected readonly progress = computed(() => {
    const since = this.secondsSinceRefresh();
    return since === null ? 0 : Math.min(100, Math.round((since / REFRESH_SECONDS) * 100));
  });

  protected readonly tone = computed(() => {
    const health = this.state().health;
    return health ? toneOf(health.headline.status) : 'neutral';
  });

  protected readonly routeLabel = computed(() => this.route() ?? this.state().health?.routeId ?? null);

  protected readonly departsLabel = computed(() => {
    const at = this.departs();
    return at ? formatLocalDeparture(at, this.language.current) : null;
  });

  protected readonly delay = describeDelay;
  protected readonly minutes = minutesFromSeconds;
  protected readonly crowding = crowdingLevel;

  protected time(iso: string): string {
    return formatInstant(iso, this.language.current);
  }
}

function toneOf(status: HeadlineStatus): 'ok' | 'warn' | 'bad' | 'neutral' {
  switch (status) {
    case 'LIVE':
      return 'ok';
    case 'CANCELLED':
      return 'bad';
    case 'LIVE_DATA_UNAVAILABLE':
    case 'TRIP_FINISHED':
      return 'neutral';
    default:
      return 'warn';
  }
}
