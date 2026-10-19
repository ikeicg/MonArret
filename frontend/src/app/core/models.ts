export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
}

export interface Route {
  routeId: string;
  longRouteName: string;
}

export interface Direction {
  route_id: string;
  directionId: number;
  direction: string;
}

export interface Departure {
  tripId: string;
  stopId: string;
  stopName: string;
  // Local Montreal time, no zone: 2026-10-04T18:36:00
  departureTime: string;
  stopSequence: number;
}

export type HeadlineStatus =
  | 'LIVE_DATA_UNAVAILABLE'
  | 'CANCELLED'
  | 'TRIP_FINISHED'
  | 'SIGNAL_LOST'
  | 'LIVE'
  | 'NOT_TRACKED_YET'
  | 'STARTED_LATE'
  | 'NO_LIVE_BUS_FOUND'
  | 'NO_LIVE_INFO';

export type StopStatus = 'SKIPPED' | 'NO_LIVE_INFO' | 'SCHEDULED' | 'UNKNOWN';
export type ProgressState = 'BEFORE_STOP' | 'AT_STOP' | 'PASSED' | 'MAY_HAVE_PASSED' | 'UNKNOWN';
export type MovementStatus = 'MOVING' | 'STATIONARY' | 'UNKNOWN';
export type TimingStatus = 'EARLY' | 'LATE' | 'NORMAL' | 'NO_PREDICTION';
export type RouteStatus = 'ON_ROUTE' | 'NOT_ON_ROUTE' | 'UNKNOWN';
export type Occupancy =
  | 'EMPTY'
  | 'MANY_SEATS_AVAILABLE'
  | 'FEW_SEATS_AVAILABLE'
  | 'STANDING_ROOM_ONLY'
  | 'CRUSHED_STANDING_ROOM_ONLY'
  | 'FULL'
  | 'NOT_ACCEPTING_PASSENGERS'
  | 'NO_DATA_AVAILABLE'
  | 'NOT_BOARDABLE'
  | 'UNKNOWN';

export interface Headline {
  status: HeadlineStatus;
  positionAgeSeconds: number | null;
}

export interface BusProgress {
  state: ProgressState;
  stopsAway: number | null;
  nextStopName: string | null;
  movement: MovementStatus;
  stationarySeconds: number | null;
}

export interface StopTiming {
  scheduled: string;
  predicted: string | null;
  status: TimingStatus;
  differenceSeconds: number | null;
  estimate: boolean;
}

// Items that do not apply are null.
export interface TripHealth {
  tripId: string;
  routeId: string | null;
  directionId: number | null;
  stopSequence: number;
  updatedAt: string | null;
  headline: Headline;
  stopStatus: StopStatus | null;
  progress: BusProgress | null;
  timing: StopTiming | null;
  crowding: Occupancy | null;
  routeStatus: RouteStatus | null;
}
