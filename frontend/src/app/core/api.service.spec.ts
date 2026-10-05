import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('looks a route up by id', () => {
    api.route('24').subscribe();
    http.expectOne('/search/route/24').flush({ success: true, message: '', data: null });
  });

  it('asks for departures with the route and direction', () => {
    api.departures('24', 1).subscribe();
    const req = http.expectOne((r) => r.url === '/search/stop-times');
    expect(req.request.params.get('routeId')).toBe('24');
    expect(req.request.params.get('directionId')).toBe('1');
    req.flush({ success: true, message: '', data: [] });
  });

  it('asks for a trip health page by trip and stop sequence', () => {
    api.tripHealth('300155479', 15).subscribe();
    http.expectOne('/trips/300155479/stops/15/health').flush({ success: true, message: '', data: null });
  });

  it('escapes ids placed in the path', () => {
    api.route('a/b').subscribe();
    http.expectOne('/search/route/a%2Fb').flush({ success: false, message: '', data: null });
  });
});
