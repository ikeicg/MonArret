import { Component, input } from '@angular/core';

// A heartbeat line ending in a dot: the trip's health, live. Colours follow the banner it sits in.
@Component({
  selector: 'app-logo',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="9" style="fill: var(--chip-bg, #ffd23f)" />
      <path
        d="M5 17h5l3-7 4 13 3-6h7"
        fill="none"
        stroke-width="2.4"
        stroke-linecap="round"
        stroke-linejoin="round"
        style="stroke: var(--chip-fg, #111827)"
      />
      <circle cx="27" cy="17" r="1.9" style="fill: var(--chip-fg, #111827)" />
    </svg>
  `,
  host: { style: 'display: inline-flex' },
})
export class Logo {
  readonly size = input(30);
}
