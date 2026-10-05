import { Occupancy } from './models';

const MONTREAL = 'America/Montreal';

// The health payload carries UTC instants; riders read Montreal clock time.
export function formatInstant(iso: string, lang: string): string {
  return new Intl.DateTimeFormat(lang, {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: MONTREAL,
  }).format(new Date(iso));
}

// The departures list already carries Montreal local time without a zone: 2026-10-04T18:36:00.
export function formatLocalDeparture(localDateTime: string): string {
  return localDateTime.substring(11, 16);
}

export function minutesFromSeconds(seconds: number): number {
  return Math.max(1, Math.round(Math.abs(seconds) / 60));
}

export interface Delay {
  kind: 'onTime' | 'late' | 'early';
  minutes: number;
}

// Predicted minus scheduled; under half a minute either way reads as on time.
export function describeDelay(differenceSeconds: number): Delay {
  if (Math.abs(differenceSeconds) < 30) return { kind: 'onTime', minutes: 0 };
  return {
    kind: differenceSeconds > 0 ? 'late' : 'early',
    minutes: minutesFromSeconds(differenceSeconds),
  };
}

// 0 means no usable level; 1 to 3 is how crowded the bus is.
export function crowdingLevel(occupancy: Occupancy): 0 | 1 | 2 | 3 {
  switch (occupancy) {
    case 'EMPTY':
    case 'MANY_SEATS_AVAILABLE':
      return 1;
    case 'FEW_SEATS_AVAILABLE':
      return 2;
    case 'STANDING_ROOM_ONLY':
    case 'CRUSHED_STANDING_ROOM_ONLY':
    case 'FULL':
      return 3;
    default:
      return 0;
  }
}
