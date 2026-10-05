import { Injectable, signal } from '@angular/core';

// The route, direction and stop a rider travelled with. Not a trip: a stop stays valid from day to day.
export interface RecentStop {
  routeId: string;
  routeName: string;
  directionId: number;
  directionName: string;
  stopId: string;
  stopName: string;
}

const STORAGE_KEY = 'csm.recent-stops';
const MAX_STOPS = 3;

@Injectable({ providedIn: 'root' })
export class RecentStops {
  private readonly stops = signal<RecentStop[]>(read());
  readonly list = this.stops.asReadonly();

  add(stop: RecentStop): void {
    const next = [stop, ...read().filter((s) => !same(s, stop))].slice(0, MAX_STOPS);
    this.stops.set(next);
    write(next);
  }
}

function same(a: RecentStop, b: RecentStop): boolean {
  return a.routeId === b.routeId && a.directionId === b.directionId && a.stopId === b.stopId;
}

function isRecentStop(value: unknown): value is RecentStop {
  const s = value as RecentStop;
  return (
    typeof s?.routeId === 'string' &&
    typeof s.routeName === 'string' &&
    typeof s.directionId === 'number' &&
    typeof s.directionName === 'string' &&
    typeof s.stopId === 'string' &&
    typeof s.stopName === 'string'
  );
}

function read(): RecentStop[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter(isRecentStop) : [];
  } catch {
    return [];
  }
}

function write(stops: RecentStop[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(stops));
  } catch {
    // storage unavailable
  }
}
