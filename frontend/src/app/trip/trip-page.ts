import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { map, scan, switchMap } from 'rxjs';

import { ApiService } from '../core/api.service';
import { crowdingLevel, describeDelay, formatInstant } from '../core/format';
import { LanguageService } from '../core/language';
import { HeadlineStatus, TripHealth } from '../core/models';
import { poll } from '../core/poll';

interface TripState {
  health: TripHealth | null;
  // The latest refresh failed; health, if any, is from an earlier one.
  failed: boolean;
  refreshedAt: number | null;
}

const INITIAL: TripState = { health: null, failed: false, refreshedAt: null };

@Component({
  selector: 'app-trip-page',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './trip-page.html',
})
export class TripPage {
  private readonly api = inject(ApiService);
  private readonly language = inject(LanguageService);

  // Bound from the route parameters.
  readonly tripId = input.required<string>();
  readonly stopSequence = input.required<string>();

  protected readonly state = toSignal(
    toObservable(computed(() => ({ tripId: this.tripId(), stopSequence: Number(this.stopSequence()) }))).pipe(
      switchMap(({ tripId, stopSequence }) =>
        poll(() => this.api.tripHealth(tripId, stopSequence).pipe(map((response) => response.data))).pipe(
          scan(
            (state, result): TripState =>
              result.ok && result.value
                ? { health: result.value, failed: false, refreshedAt: Date.now() }
                : { ...state, failed: true },
            INITIAL,
          ),
        ),
      ),
    ),
    { initialValue: INITIAL },
  );

  protected readonly refreshed = computed(() => {
    const at = this.state().refreshedAt;
    return at === null ? null : formatInstant(new Date(at).toISOString(), this.language.current);
  });

  protected time(iso: string): string {
    return formatInstant(iso, this.language.current);
  }

  protected readonly delay = describeDelay;
  protected readonly crowding = crowdingLevel;

  protected tone(status: HeadlineStatus): string {
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
}
