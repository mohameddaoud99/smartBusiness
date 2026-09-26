import { Component, ViewChild, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { NumberingService } from '../../../core/services/numbering.service';
import { NumberingSequenceResponse } from './numbering.model';
import { NumberingFormComponent } from './numbering-form/numbering-form.component';

@Component({
  selector: 'app-numbering',
  standalone: true,
  imports: [
    TableModule, ButtonModule, TagModule, TooltipModule,
    PageHeaderComponent, HasPermissionDirective, NumberingFormComponent
  ],
  templateUrl: './numbering.component.html',
  styleUrl: './numbering.component.scss'
})
export class NumberingComponent {

  private readonly numberingService = inject(NumberingService);

  @ViewChild(NumberingFormComponent) form!: NumberingFormComponent;

  readonly sequences = signal<NumberingSequenceResponse[]>([]);
  readonly loading = signal(true);

  constructor() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.numberingService.findAll().subscribe({
      next: sequences => {
        this.sequences.set(sequences);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openEdit(sequence: NumberingSequenceResponse) {
    this.form.open(sequence);
  }
}
