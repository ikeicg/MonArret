import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { ApiService } from '../core/api.service';
import { formatInstant, formatLocalDeparture } from '../core/format';
import { LanguageService } from '../core/language';
import { Departure, Direction, Route } from '../core/models';

interface StopOption {
  stopId: string;
  stopName: string;
}

@Component({
  selector: 'app-pick-page',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './pick-page.html',
})
export class PickPage {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly language = inject(LanguageService);

  protected readonly route = signal<Route | null>(null);
  protected readonly routeMissing = signal(false);
  protected readonly directions = signal<Direction[]>([]);
  protected readonly directionId = signal<number | null>(null);
  protected readonly departures = signal<Departure[]>([]);
  protected readonly stopId = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);

  protected readonly formatTime = formatLocalDeparture;

  // When the departures were fetched; the list covers the hour from then.
  private readonly fetchedAt = signal<Date | null>(null);
  protected readonly windowFrom = computed(() => this.clock(0));
  protected readonly windowTo = computed(() => this.clock(60));

  // Distinct stops in the order the trips reach them.
  protected readonly stops = computed<StopOption[]>(() => {
    const firstSeen = new Map<string, { stopName: string; sequence: number }>();
    for (const d of this.departures()) {
      const seen = firstSeen.get(d.stopId);
      if (!seen || d.stopSequence < seen.sequence) {
        firstSeen.set(d.stopId, { stopName: d.stopName, sequence: d.stopSequence });
      }
    }
    return [...firstSeen.entries()]
      .sort((a, b) => a[1].sequence - b[1].sequence)
      .map(([stopId, { stopName }]) => ({ stopId, stopName }));
  });

  protected readonly times = computed(() =>
    this.departures()
      .filter((d) => d.stopId === this.stopId())
      .sort((a, b) => a.departureTime.localeCompare(b.departureTime)),
  );

  protected findRoute(value: string): void {
    const routeId = value.trim();
    if (!routeId) return;

    this.reset();
    this.loading.set(true);
    this.api
      .route(routeId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          if (!response.success || !response.data) {
            this.routeMissing.set(true);
            this.loading.set(false);
            return;
          }
          this.route.set(response.data);
          this.loadDirections(response.data.routeId);
        },
        error: () => this.fail(),
      });
  }

  protected chooseDirection(directionId: number): void {
    const route = this.route();
    if (!route) return;

    this.directionId.set(directionId);
    this.departures.set([]);
    this.stopId.set(null);
    this.loading.set(true);
    this.api
      .departures(route.routeId, directionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.fetchedAt.set(new Date());
          this.departures.set(response.data ?? []);
          this.loading.set(false);
        },
        error: () => this.fail(),
      });
  }

  protected chooseStop(value: string): void {
    this.stopId.set(value === '' ? null : value);
  }

  private loadDirections(routeId: string): void {
    this.api
      .directions(routeId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          const directions = response.data ?? [];
          this.directions.set(directions);
          this.loading.set(false);
          if (directions.length === 1) {
            this.chooseDirection(directions[0].directionId);
          }
        },
        error: () => this.fail(),
      });
  }

  private clock(offsetMinutes: number): string {
    const at = this.fetchedAt();
    if (!at) return '';
    return formatInstant(new Date(at.getTime() + offsetMinutes * 60_000).toISOString(), this.language.current);
  }

  private reset(): void {
    this.route.set(null);
    this.routeMissing.set(false);
    this.directions.set([]);
    this.directionId.set(null);
    this.departures.set([]);
    this.stopId.set(null);
    this.failed.set(false);
  }

  private fail(): void {
    this.failed.set(true);
    this.loading.set(false);
  }
}
