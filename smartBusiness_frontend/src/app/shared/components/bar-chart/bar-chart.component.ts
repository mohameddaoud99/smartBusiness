import { Component, Input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

export interface BarPoint {
  label: string;
  value: number;
}

/**
 * A row of bars, one per point — the little chart of the dashboard. Plain HTML and CSS on purpose: a chart library
 * is a dependency for what is, here, six numbers. Heights are relative to the biggest bar; a bar that is zero or
 * negative is drawn as a hairline, and every bar says its value on hover.
 */
@Component({
  selector: 'app-bar-chart',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './bar-chart.component.html',
  styleUrl: './bar-chart.component.scss'
})
export class BarChartComponent {

  @Input({ required: true }) points: BarPoint[] = [];
  /** What the chart is about, for a screen reader. */
  @Input() ariaLabel = 'Bar chart';

  /** The height of a bar, as a share of the tallest one. */
  height(point: BarPoint): number {
    const max = Math.max(...this.points.map(p => p.value), 0);
    return max > 0 && point.value > 0 ? (point.value / max) * 100 : 0;
  }
}
