import { Routes } from '@angular/router';

import { PickPage } from './pick/pick-page';
import { TripPage } from './trip/trip-page';

export const routes: Routes = [
  { path: '', component: PickPage },
  { path: 'trip/:tripId/stop/:stopSequence', component: TripPage },
  { path: '**', redirectTo: '' },
];
