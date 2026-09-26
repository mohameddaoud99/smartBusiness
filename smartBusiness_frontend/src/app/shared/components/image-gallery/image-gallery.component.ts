import { Component, ElementRef, EventEmitter, Input, Output, ViewChild, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';

const ACCEPTED_TYPES = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_SIZE_BYTES = 1_000_000;

/**
 * A row of photo slots (4 by default): each filled slot shows its picture with a remove
 * button, the next free one opens the file picker. Like app-image-upload, it only picks
 * and validates the file — the HTTP calls stay with the parent, which decides what
 * "add" and "remove" mean (upload now, or keep it aside until the record exists).
 */
@Component({
  selector: 'app-image-gallery',
  standalone: true,
  imports: [ButtonModule],
  templateUrl: './image-gallery.component.html',
  styleUrl: './image-gallery.component.scss'
})
export class ImageGalleryComponent {

  @Input({ required: true }) label = '';
  @Input() images: string[] = [];
  @Input() max = 4;
  @Input() saving = false;
  @Input() disabled = false;

  @Output() fileSelected = new EventEmitter<File>();
  @Output() removeRequested = new EventEmitter<number>();

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  readonly errorMessage = signal('');

  get emptySlots(): number[] {
    return Array.from({ length: Math.max(this.max - this.images.length, 0) }, (_, i) => i);
  }

  pickFile() {
    if (this.disabled || this.saving) {
      return;
    }
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
