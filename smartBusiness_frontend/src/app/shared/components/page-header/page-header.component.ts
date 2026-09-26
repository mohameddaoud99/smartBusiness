import { Component, Input } from '@angular/core';

/**
 * Standard header used at the top of every ERP page.
 * Actions are projected: <app-page-header ...><p-button ... /></app-page-header>
 */
@Component({
  selector: 'app-page-header',
  standalone: true,
  templateUrl: './page-header.component.html',
  styleUrl: './page-header.component.scss'
})
export class PageHeaderComponent {
  @Input({ required: true }) title = '';
  @Input() description = '';
}
