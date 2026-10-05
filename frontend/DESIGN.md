# Design system

Direction: **Bright Day**. A light page, a solid coloured banner at the top of each screen, white tiles below.

## Where things live

- `src/styles/_tokens.scss`: every colour, radius, shadow, spacing and type size, as CSS custom properties.
- `src/styles/_base.scss`: reset, body, focus ring, page container.
- `src/styles/_components.scss`: banner, tiles, pills, chips, fields, buttons, refresh strip, brand bar.
- `src/app/shared/`: `Logo` and `BrandBar` (logo plus EN/FR toggle).
- Fonts are bundled from `@fontsource-variable` (Bricolage Grotesque for display, Figtree for text): no external font requests.

## Rules

- Use tokens, never raw hex values, in components and templates.
- A banner sets `--banner-bg` and `--banner-fg`; everything inside it (logo, toggle, refresh strip) follows those. The trip banner's colour comes from the headline status: green live, amber attention, red cancelled, slate unavailable or finished.
- Normal information is a plain white tile. A tile that reports a problem uses `tile--problem` (red tint: skipped stop, off route, passed stop, bus not boardable) or `tile--caution` (amber tint: late or early). Problems also carry an icon and bolder text, so colour is never the only signal.
- Text on solid colours meets 4.5:1; muted text is `--color-text-muted`.
- All copy goes through translation keys (`public/i18n`). Times use `Intl` with the active language (12-hour in English, 24-hour in French).
- Light only for now. Dark mode would add a second set of token values, not new components.
