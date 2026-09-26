import { Component, ElementRef, EventEmitter, Input, Output, ViewChild, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';

const ACCEPTED_TYPES = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_SIZE_BYTES = 1_000_000;

/**
 * A single image slot with preview, upload and remove — used on the company settings
 * page (logo, stamp) and on the product form (photo). The actual HTTP call stays with
 * the parent, since each caller hits a different endpoint; this component only picks
 * and validates the file.
 */
@Component({
  selector: 'app-image-upload',
  standalone: true,
  imports: [ButtonModule],
  templateUrl: './image-upload.component.html',
  styleUrl: './image-upload.component.scss'
})
export class ImageUploadComponent {

  @Input({ required: true }) label = '';
  @Input() hint = 'PNG, JPEG or WEBP, up to 1 MB';
  @Input() imageUri?: string;
  @Input() saving = false;
  @Input() disabled = false;

  @Output() fileSelected = new EventEmitter<File>();
  @Output() removeRequested = new EventEmitter<void>();

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  readonly errorMessage = signal('');

  pickFile() {
    this.errorMessage.set('');
    this.fileInput.nativeElement.click();
  }

  onFileChosen(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // lets the same file be re-picked after a rejection

    if (!file) {
      return;
    }
    if (!ACCEPTED_TYPES.includes(file.type)) {
      this.errorMessage.set('Only PNG, JPEG or WEBP images are allowed.');
      return;
    }
    if (file.size > MAX_SIZE_BYTES) {
      this.errorMessage.set('Image must not exceed 1 MB.');
      return;
    }

    this.errorMessage.set('');
    this.fileSelected.emit(file);
  }
}
