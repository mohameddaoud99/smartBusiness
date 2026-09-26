import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService } from 'primeng/api';

import { ProductFormComponent } from './product-form.component';
import { ProductResponse } from '../product.model';

describe('ProductFormComponent', () => {

  let fixture: ComponentFixture<ProductFormComponent>;
  let component: ProductFormComponent;
  let httpMock: HttpTestingController;

  const url = 'http://localhost:8080/api/products';

  const existing: ProductResponse = {
    id: 5,
    reference: 'P-0005',
    name: 'USB-C Cable',
    kind: 'GOOD',
    purpose: 'SALE',
    unit: 'PIECE',
    category: { id: 1, name: 'Accessories' },
    brand: { id: 2, name: 'Anker' },
    salePrice: 12.5,
    purchasePrice: 7,
    allowNegativeStock: true,
    defaultTaxes: [{ id: 9, name: 'TVA 19%' }],
    createdAt: '2026-01-01T10:00:00',
    updatedAt: '2026-01-01T10:00:00'
  };

  const withPhotos: ProductResponse = {
    ...existing,
    imageDataUri: 'data:image/png;base64,AA==',
    images: [
      { id: 11, dataUri: 'data:image/png;base64,AA==' },
      { id: 12, dataUri: 'data:image/png;base64,AQ==' }
    ]
  };

  /** Opening a saved product also fetches all its photos — the list only carries the cover. */
  function openEditing(product: ProductResponse = existing, full: ProductResponse = product) {
    component.open(product);
    httpMock.expectOne(`${url}/${product.id}`).flush(full);
  }

  function png(name = 'photo.png'): File {
    return new File([new Uint8Array([1, 2, 3])], name, { type: 'image/png' });
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        MessageService,
        ConfirmationService
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProductFormComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    fixture.detectChanges();

    httpMock.expectOne('http://localhost:8080/api/categories').flush([]);
    httpMock.expectOne('http://localhost:8080/api/brands').flush([]);
    httpMock.expectOne('http://localhost:8080/api/settings/taxes').flush([]);
  });

  afterEach(() => httpMock.verify());

  it('starts invalid because the name is required', () => {
    component.open();
    expect(component.form.valid).toBeFalse();
    expect(component.form.controls['name'].hasError('required')).toBeTrue();
  });

  it('defaults to a physical product priced by the piece', () => {
    component.open();
    expect(component.form.value.type).toBe('GOOD');
    expect(component.isGood).toBeTrue();
    expect(component.form.value.unit).toBe('PIECE');
    expect(component.form.value.purpose).toBe('SALE');
  });

  it('prefills identity, catalogue links and taxes when editing', () => {
    openEditing();
    expect(component.editingId()).toBe(5);
    expect(component.form.value.name).toBe('USB-C Cable');
    expect(component.form.value.categoryId).toBe(1);
    expect(component.form.value.brandId).toBe(2);
    expect(component.form.value.taxIds).toEqual([9]);
  });

  it('does not call the API while the form is invalid', () => {
    component.open();
    component.submit();
    httpMock.expectNone(url);
    expect(component.form.touched).toBeTrue();
  });

  it('POSTs a new product with the resolved kind and catalogue links', () => {
    component.open();
    component.form.patchValue({ name: 'USB-C Cable', categoryId: 1, taxIds: [9] });

    component.submit();

    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.kind).toBe('GOOD');
    expect(req.request.body.categoryId).toBe(1);
    expect(req.request.body.taxIds).toEqual([9]);
    req.flush({ ...existing });
  });

  it('sends the minimum stock of a product, and none for a service', () => {
    component.open();
    component.form.patchValue({ name: 'USB-C Cable', minStock: 5 });
    component.submit();
    const good = httpMock.expectOne(url);
    expect(good.request.body.minStock).toBe(5);
    good.flush({ ...existing });

    component.open();
    component.form.patchValue({ name: 'Consulting', type: 'SERVICE', minStock: 5 });
    component.submit();
    const service = httpMock.expectOne(url);
    expect(service.request.body.minStock).toBeNull();
    service.flush({ ...existing });
  });

  it('PUTs when editing', () => {
    openEditing();
    component.form.controls['name'].setValue('USB-C Cable 2m');

    component.submit();

    const req = httpMock.expectOne(`${url}/5`);
    expect(req.request.method).toBe('PUT');
    req.flush({ ...existing });
  });

  it('releases the saving state on an API error', () => {
    component.open();
    component.form.patchValue({ name: 'USB-C Cable' });
    component.submit();
    expect(component.saving()).toBeTrue();

    httpMock.expectOne(url).flush(
      { message: 'A product with this reference already exists' },
      { status: 409, statusText: 'Conflict' }
    );

    expect(component.saving()).toBeFalse();
  });

  // ----- Photos -----

  it('shows every saved photo of the product being edited', () => {
    openEditing(existing, withPhotos);
    expect(component.galleryImages()).toEqual([
      'data:image/png;base64,AA==',
      'data:image/png;base64,AQ=='
    ]);
  });

  it('shows the not-yet-uploaded photos while creating, in the order they were chosen', () => {
    component.open();
    component.pendingImages.set([
      { file: png('a.png'), preview: 'data:image/png;base64,QQ==' },
      { file: png('b.png'), preview: 'data:image/png;base64,Qg==' }
    ]);
    expect(component.galleryImages()).toEqual([
      'data:image/png;base64,QQ==',
      'data:image/png;base64,Qg=='
    ]);
  });

  it('uploads the photos chosen at creation one after the other, once the product exists', () => {
    component.open();
    component.form.patchValue({ name: 'USB-C Cable' });
    component.pendingImages.set([
      { file: png('a.png'), preview: 'a' },
      { file: png('b.png'), preview: 'b' }
    ]);

    component.submit();
    httpMock.expectOne(url).flush({ ...existing });

    const first = httpMock.expectOne(`${url}/5/images`);
    expect(first.request.method).toBe('POST');
    expect(first.request.body instanceof FormData).toBeTrue();
    httpMock.expectNone(`${url}/5/images`); // the second waits for the first
    first.flush({ ...existing });

    httpMock.expectOne(`${url}/5/images`).flush({ ...existing });
  });

  it('keeps the product saved when a photo upload fails, and still tries the next one', () => {
    component.open();
    component.form.patchValue({ name: 'USB-C Cable' });
    component.pendingImages.set([
      { file: png('a.png'), preview: 'a' },
      { file: png('b.png'), preview: 'b' }
    ]);
    let saved = false;
    component.saved.subscribe(() => saved = true);

    component.submit();
    httpMock.expectOne(url).flush({ ...existing });
    httpMock.expectOne(`${url}/5/images`)
      .flush({ message: 'boom' }, { status: 422, statusText: 'Unprocessable' });
    httpMock.expectOne(`${url}/5/images`).flush({ ...existing });

    expect(saved).toBeTrue();
    expect(component.saving()).toBeFalse();
  });

  it('adds a photo straight away when editing, and refreshes the photos', () => {
    openEditing();
    let saved = false;
    component.saved.subscribe(() => saved = true);

    component.onImageSelected(png());

    const req = httpMock.expectOne(`${url}/5/images`);
    expect(req.request.method).toBe('POST');
    req.flush(withPhotos);

    expect(component.galleryImages().length).toBe(2);
    expect(component.imageSaving()).toBeFalse();
    expect(saved).toBeTrue();
  });

  it('removes a not-yet-uploaded photo without calling the API', () => {
    component.open();
    component.pendingImages.set([
      { file: png('a.png'), preview: 'a' },
      { file: png('b.png'), preview: 'b' }
    ]);

    component.confirmRemoveImage(0);

    expect(component.galleryImages()).toEqual(['b']);
  });

  it('removes a saved photo by its id once confirmed', () => {
    openEditing(existing, withPhotos);
    const confirmation = TestBed.inject(ConfirmationService);
    spyOn(confirmation, 'confirm').and.callFake(options => options.accept?.());

    component.confirmRemoveImage(1);

    const req = httpMock.expectOne(`${url}/5/images/12`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ ...withPhotos, images: [withPhotos.images![0]] });
    expect(component.galleryImages().length).toBe(1);
  });
});
