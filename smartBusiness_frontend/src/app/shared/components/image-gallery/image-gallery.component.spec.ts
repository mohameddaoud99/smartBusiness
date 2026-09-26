import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { ImageGalleryComponent } from './image-gallery.component';

describe('ImageGalleryComponent', () => {

  let fixture: ComponentFixture<ImageGalleryComponent>;
  let component: ImageGalleryComponent;

  function pick(file: File) {
    const input = fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement;
    const transfer = new DataTransfer();
    transfer.items.add(file);
    input.files = transfer.files;
    input.dispatchEvent(new Event('change'));
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ImageGalleryComponent],
      providers: [provideNoopAnimations()]
    }).compileComponents();

    fixture = TestBed.createComponent(ImageGalleryComponent);
    component = fixture.componentInstance;
    component.label = 'Photos';
    fixture.detectChanges();
  });

  it('offers four empty slots when there is no photo', () => {
    expect(fixture.nativeElement.querySelectorAll('.slot.empty').length).toBe(4);
    expect(fixture.nativeElement.querySelectorAll('.slot.filled').length).toBe(0);
  });

  it('fills the slots with the photos and leaves the rest empty', () => {
    component.images = ['data:image/png;base64,AA==', 'data:image/png;base64,AQ=='];
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.slot.filled').length).toBe(2);
    expect(fixture.nativeElement.querySelectorAll('.slot.empty').length).toBe(2);
    expect(fixture.nativeElement.querySelector('.gallery-count').textContent).toContain('2/4');
  });

  it('marks the first photo as the cover, and only that one', () => {
    component.images = ['a', 'b', 'c'];
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.cover-badge').length).toBe(1);
  });

  it('offers no empty slot once the limit is reached', () => {
    component.images = ['a', 'b', 'c', 'd'];
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.slot.empty').length).toBe(0);
  });

  it('emits a valid image', () => {
    const chosen: File[] = [];
    component.fileSelected.subscribe(file => chosen.push(file));

    pick(new File([new Uint8Array([1])], 'photo.png', { type: 'image/png' }));

    expect(chosen.length).toBe(1);
    expect(component.errorMessage()).toBe('');
  });

  it('refuses a file that is not an image', () => {
    const chosen: File[] = [];
    component.fileSelected.subscribe(file => chosen.push(file));

    pick(new File(['hello'], 'notes.txt', { type: 'text/plain' }));

    expect(chosen.length).toBe(0);
    expect(component.errorMessage()).toContain('PNG, JPEG or WEBP');
  });

  it('refuses an image over 1 MB', () => {
    const chosen: File[] = [];
    component.fileSelected.subscribe(file => chosen.push(file));

    pick(new File([new Uint8Array(1_000_001)], 'big.png', { type: 'image/png' }));

    expect(chosen.length).toBe(0);
    expect(component.errorMessage()).toContain('1 MB');
  });

  it('reports which photo the user wants to remove', () => {
    component.images = ['a', 'b'];
    fixture.detectChanges();
    const removed: number[] = [];
    component.removeRequested.subscribe(index => removed.push(index));

    const buttons = fixture.nativeElement.querySelectorAll('.remove-button button');
    (buttons[1] as HTMLButtonElement).click();

    expect(removed).toEqual([1]);
  });
});
