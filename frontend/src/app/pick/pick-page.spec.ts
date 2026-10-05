import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';

import { TranslocoHttpLoader } from '../core/language';
import { RecentStops } from '../core/recent-stops';
import { PickPage } from './pick-page';

const stopTimes = (stopId: string, stopSequence: number) => ({
  tripId: `trip-${stopId}`,
  stopId,
  stopName: `Stop ${stopId}`,
  departureTime: '2026-10-04T19:23:00',
  stopSequence,
});

describe('PickPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [PickPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({ config: { availableLangs: ['en', 'fr'], defaultLang: 'en' }, loader: TranslocoHttpLoader }),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  it('restores route, direction and stop from a recent stop, leaving the departure to the rider', async () => {
    TestBed.inject(RecentStops).add({
      routeId: '24',
      routeName: 'Sherbrooke',
      directionId: 1,
      directionName: 'Ouest',
      stopId: 'B',
      stopName: 'Stop B',
    });
    const fixture = TestBed.createComponent(PickPage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('.recent-list button')!.click();

    http.expectOne('/search/route-directions/24').flush({
      success: true,
      message: '',
      data: [
        { route_id: '24', directionId: 0, direction: 'Est' },
        { route_id: '24', directionId: 1, direction: 'Ouest' },
      ],
    });
    const request = http.expectOne((r) => r.url === '/search/stop-times');
    expect(request.request.params.get('directionId')).toBe('1');
    request.flush({ success: true, message: '', data: [stopTimes('A', 1), stopTimes('B', 2)] });
    await fixture.whenStable();

    expect(page.querySelector('select')!.value).toBe('B');
    expect(page.querySelectorAll('.time-grid li')).toHaveLength(1);
  });
});
