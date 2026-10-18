import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-site-footer',
  imports: [TranslocoPipe],
  template: `
    <footer class="site-footer">
      <p>
        {{ 'footer.dataBy' | transloco }}
        <a [href]="'footer.stmUrl' | transloco" target="_blank" rel="noopener">{{ 'footer.stm' | transloco }}</a>,
        <a [href]="'footer.licenceUrl' | transloco" target="_blank" rel="noopener">{{ 'footer.licence' | transloco }}</a>.
      </p>
      <p>{{ 'footer.disclaimer' | transloco }}</p>
    </footer>
  `,
})
export class SiteFooter {}
