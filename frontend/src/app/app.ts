import { Component } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { online$ } from './core/poll';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, TranslocoPipe],
  template: `
    @if (!online()) {
      <div class="offline-bar" role="status">{{ 'net.offline' | transloco }}</div>
    }
    <router-outlet />
  `,
})
export class App {
  protected readonly online = toSignal(online$(), { initialValue: navigator.onLine });
}
