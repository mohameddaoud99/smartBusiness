import { TestBed, ComponentFixture } from '@angular/core/testing';

import { BarChartComponent } from './bar-chart.component';

describe('BarChartComponent', () => {

  let fixture: ComponentFixture<BarChartComponent>;
  let component: BarChartComponent;

  function render(points: { label: string; value: number }[]) {
    fixture = TestBed.createComponent(BarChartComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('points', points);
    fixture.detectChanges();
  }

  const columns = () => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.bar-column'));

  it('draws one column per point, in order, under its label', () => {
    render([{ label: 'Jan', value: 10 }, { label: 'Feb', value: 20 }]);

    expect(columns().map(column => column.querySelector('.bar-label')!.textContent)).toEqual(['Jan', 'Feb']);
  });

  it('sizes the bars against the biggest one', () => {
    render([{ label: 'Jan', value: 10 }, { label: 'Feb', value: 40 }, { label: 'Mar', value: 20 }]);

    expect(component.height({ label: 'Feb', value: 40 })).toBe(100);
    expect(component.height({ label: 'Jan', value: 10 })).toBe(25);
    expect(component.height({ label: 'Mar', value: 20 })).toBe(50);
  });

  it('draws a hairline for a zero or a negative value, and never a negative height', () => {
    render([{ label: 'Jan', value: 0 }, { label: 'Feb', value: -5 }, { label: 'Mar', value: 10 }]);

    expect(component.height({ label: 'Jan', value: 0 })).toBe(0);
    expect(component.height({ label: 'Feb', value: -5 })).toBe(0);
    const bars = (fixture.nativeElement as HTMLElement).querySelectorAll('.bar');
    expect(bars[0].classList).toContain('is-empty');
    expect(bars[1].classList).toContain('is-empty');
    expect(bars[2].classList).not.toContain('is-empty');
  });

  it('draws nothing tall when every value is zero', () => {
    render([{ label: 'Jan', value: 0 }, { label: 'Feb', value: 0 }]);

    expect(component.height({ label: 'Jan', value: 0 })).toBe(0);
  });

  it('says each value on hover, and what the chart is about to a screen reader', () => {
    render([{ label: 'Jan', value: 1234.5 }]);
    fixture.componentRef.setInput('ariaLabel', 'Net sales');
    fixture.detectChanges();

    expect(columns()[0].getAttribute('title')).toBe('Jan: 1,234.500');
    expect((fixture.nativeElement as HTMLElement).querySelector('.bars')!.getAttribute('aria-label')).toBe('Net sales');
  });

  it('draws an empty chart without failing', () => {
    render([]);

    expect(columns().length).toBe(0);
  });
});
