import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoLoader, provideTransloco } from '@jsverse/transloco';
import { of } from 'rxjs';

import en from '../../../public/i18n/en.json';
import fr from '../../../public/i18n/fr.json';
import { TripHealth } from '../core/models';
import { TripPage } from './trip-page';

class JsonLoader implements TranslocoLoader {
  getTranslation(lang: string) {
    return of(lang === 'fr' ? fr : en);
  }
}

function health(stationarySeconds: number | null): TripHealth {
  return {
    tripId: '300234200',
    routeId: '371',
    directionId: 0,
    stopSequence: 57,
    updatedAt: '2026-10-19T07:00:00Z',
    headline: { status: 'LIVE', positionAgeSeconds: 12 },
    stopStatus: 'SCHEDULED',
    progress: { state: 'BEFORE_STOP', stopsAway: 3, nextStopName: 'Saint-Jacques', movement: 'STATIONARY', stationarySeconds },
    timing: null,
    crowding: null,
    routeStatus: 'ON_ROUTE',
  } as TripHealth;
}

describe('TripPage bus progress tile', () => {
  async function render(payload: TripHealth, inputs: Record<string, string> = {}): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [TripPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({ config: { availableLangs: ['en', 'fr'], defaultLang: 'en' }, loader: JsonLoader }),
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TripPage);
    fixture.componentRef.setInput('tripId', '300234200');
    fixture.componentRef.setInput('stopSequence', '57');
    for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
    fixture.detectChanges();
    await fixture.whenStable();
    TestBed.inject(HttpTestingController)
      .expectOne('/trips/300234200/stops/57/health')
      .flush({ success: true, message: '', data: payload });
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('says how long the bus has been stopped, in whole minutes', async () => {
    const page = await render(health(310));

    expect(page.textContent).toContain('Stopped for 5 min');
  });

  it('says nothing about it when the bus has not been stopped long', async () => {
    const page = await render(health(null));

    expect(page.textContent).not.toContain('Stopped for');
  });

  it('says the times are departures', async () => {
    const withTiming = {
      ...health(null),
      timing: {
        scheduled: '2026-10-19T06:39:26Z',
        predicted: '2026-10-19T06:41:00Z',
        status: 'NORMAL',
        differenceSeconds: 94,
        estimate: false,
      },
    } as TripHealth;

    const page = await render(withTiming, {
      stopName: 'Saint-Jacques / du Couvent',
      departs: '2026-10-19T02:39:26',
    });

    expect(page.textContent).toContain('Scheduled departure');
    expect(page.textContent).toContain('Predicted departure (STM)');
    expect(page.textContent).toContain('Departs');
  });

  it('does not claim "0 stops away" when the rider\'s skipped stop is the next one', async () => {
    const skipped = {
      ...health(null),
      stopStatus: 'SKIPPED',
      progress: { state: 'BEFORE_STOP', stopsAway: 0, nextStopName: null, movement: 'MOVING', stationarySeconds: null },
    } as TripHealth;

    const page = await render(skipped);

    expect(page.textContent).toContain('will not stop there');
    expect(page.textContent).not.toContain('0 stops away');
    expect(page.textContent).not.toContain('Heading to');
  });
});
