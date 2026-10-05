import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse, Departure, Direction, Route, TripHealth } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  route(routeId: string): Observable<ApiResponse<Route>> {
    return this.http.get<ApiResponse<Route>>(`/search/route/${encodeURIComponent(routeId)}`);
  }

  directions(routeId: string): Observable<ApiResponse<Direction[]>> {
    return this.http.get<ApiResponse<Direction[]>>(
      `/search/route-directions/${encodeURIComponent(routeId)}`,
    );
  }

  departures(routeId: string, directionId: number): Observable<ApiResponse<Departure[]>> {
    const params = new HttpParams().set('routeId', routeId).set('directionId', directionId);
    return this.http.get<ApiResponse<Departure[]>>('/search/stop-times', { params });
  }

  tripHealth(tripId: string, stopSequence: number): Observable<ApiResponse<TripHealth>> {
    return this.http.get<ApiResponse<TripHealth>>(
      `/trips/${encodeURIComponent(tripId)}/stops/${stopSequence}/health`,
    );
  }
}
