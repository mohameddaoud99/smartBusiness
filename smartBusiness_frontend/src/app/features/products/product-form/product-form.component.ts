import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { RadioButtonModule } from 'primeng/radiobutton';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { CheckboxModule } from 'primeng/checkbox';
import { ConfirmationService } from 'primeng/api';
import { catchError, concat, of, toArray } from 'rxjs';

import { ProductService } from '../../../core/services/product.service';
import { CategoryService } from '../../../core/services/category.service';
import { BrandService } from '../../../core/services/brand.service';
import { TaxService } from '../../../core/services/tax.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ImageGalleryComponent } from '../../../shared/components/image-gallery/image-gallery.component';
import { CategoryResponse } from '../../settings/categories/category.model';
import { BrandResponse } from '../../settings/brands/brand.model';
import { TaxResponse } from '../../settings/taxes/tax.model';
import {
  KIND_OPTIONS, MAX_PRODUCT_IMAGES, PURPOSE_OPTIONS, UNIT_OPTIONS,
  ProductKind, ProductRequest, ProductResponse
} from '../product.model';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, InputNumberModule,
    TextareaModule, RadioButtonModule, SelectModule, MultiSelectModule, CheckboxModule,
    ImageGalleryComponent
  ],
  templateUrl: './product-form.component.html',
  styleUrl: './product-form.component.scss'
})
export class ProductFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly productService = inject(ProductService);
  private readonly categoryService = inject(CategoryService);
  private readonly brandService = inject(BrandService);
  private readonly taxService = inject(TaxService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  @Output() saved = new EventEmitter<void>();

  readonly kindOptions = KIND_OPTIONS;
  readonly purposeOptions = PURPOSE_OPTIONS;
  readonly unitOptions = UNIT_OPTIONS;
  readonly maxImages = MAX_PRODUCT_IMAGES;

  readonly categories = signal<CategoryResponse[]>([]);
  readonly brands = signal<BrandResponse[]>([]);
  readonly taxes = signal<TaxResponse[]>([]);

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly imageSaving = signal(false);
  readonly editingId = signal<number | null>(null);
  /** The full record being edited — its photos are read from here. */
  readonly editing = signal<ProductResponse | null>(null);
  /** Photos chosen while creating: uploaded right after the product exists (it needs an id). */
  readonly pendingImages = signal<{ file: File; preview: string }[]>([]);

  /** What the gallery shows: the saved photos when editing, the not-yet-uploaded ones when creating. */
  readonly galleryImages = computed(() => this.editingId()
    ? (this.editing()?.images ?? []).map(image => image.dataUri)
    : this.pendingImages().map(image => image.preview));

  readonly title = computed(() => this.editingId() ? 'Edit product' : 'New product');

  readonly form: FormGroup = this.fb.group({
    type: ['GOOD' as ProductKind, Validators.required],
    reference: ['', Validators.maxLength(20)],
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', Validators.maxLength(4000)],
    barcode: ['', Validators.maxLength(60)],
    purpose: ['SALE', Validators.required],
    unit: ['PIECE', Validators.required],
    categoryId: [null as number | null],
    brandId: [null as number | null],
    salePrice: [null as number | null, Validators.min(0)],
    purchasePrice: [null as number | null, Validators.min(0)],
    allowNegativeStock: [true],
    minStock: [null as number | null, Validators.min(0)],
    taxIds: [[] as number[]],
    notes: ['', Validators.maxLength(2000)]
  });

  constructor() {
    this.categoryService.findAll().subscribe(categories => this.categories.set(categories));
    this.brandService.findAll().subscribe(brands => this.brands.set(brands));
    this.taxService.findAll().subscribe(taxes => this.taxes.set(taxes));
  }

  get isGood(): boolean {
    return this.form.controls['type'].value === 'GOOD';
  }

  open(product?: ProductResponse) {
    this.form.reset({
      type: 'GOOD', reference: '', purpose: 'SALE', unit: 'PIECE',
      allowNegativeStock: true, minStock: null, taxIds: []
    });
    this.pendingImages.set([]);

    if (product) {
      this.editingId.set(product.id);
      this.editing.set(product);
      this.form.patchValue({
        ...product,
        type: product.kind,
        categoryId: product.category?.id ?? null,
        brandId: product.brand?.id ?? null,
        taxIds: product.defaultTaxes.map(tax => tax.id)
      });
      // The list only carries each product's cover: fetch all of its photos
      this.productService.findById(product.id).subscribe(full => {
        if (this.editingId() === full.id) {
          this.editing.set(full);
        }
      });
    } else {
      this.editingId.set(null);
      this.editing.set(null);
    }

    this.visible.set(true);
  }

  close() {
    this.visible.set(false);
  }

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const raw = this.form.getRawValue();

    const request: ProductRequest = {
      reference: raw.reference?.trim() || undefined,
      name: raw.name,
      description: raw.description || undefined,
      barcode: raw.barcode || undefined,
      kind: raw.type,
      purpose: raw.purpose,
      unit: raw.unit,
      categoryId: raw.categoryId,
      brandId: raw.brandId,
      salePrice: raw.salePrice,
      purchasePrice: raw.purchasePrice,
      allowNegativeStock: raw.allowNegativeStock,
      minStock: this.isGood ? raw.minStock : null,
      taxIds: raw.taxIds,
      notes: raw.notes || undefined
    };

    const id = this.editingId();
    const request$ = id
      ? this.productService.update(id, request)
      : this.productService.create(request);

    request$.subscribe({
      next: product => {
        this.notification.success(
          id ? 'Product updated successfully.' : 'Product created successfully.'
        );
        const pending = this.pendingImages();
        if (!id && pending.length > 0) {
          this.uploadPendingThenFinish(product.id, pending.map(image => image.file));
          return;
        }
        this.finish();
      },
      error: () => this.saving.set(false)
    });
  }

  /**
   * The product is already saved: a failed photo upload must not undo that, only report it
   * (the error toast comes from the interceptor). One after the other, so the cover is the
   * first photo chosen and the 4-photo limit is never checked concurrently.
   */
  private uploadPendingThenFinish(productId: number, files: File[]) {
    concat(...files.map(file =>
      this.productService.addImage(productId, file).pipe(catchError(() => of(null)))
    )).pipe(toArray()).subscribe(() => this.finish());
  }

  private finish() {
    this.saving.set(false);
    this.saved.emit();
    this.close();
  }

  onImageSelected(file: File) {
    const id = this.editingId();
    if (!id) {
      const reader = new FileReader();
      reader.onload = () => this.pendingImages.update(images =>
        [...images, { file, preview: reader.result as string }]);
      reader.readAsDataURL(file);
      return;
    }
    this.imageSaving.set(true);
    this.productService.addImage(id, file).subscribe({
      next: product => {
        this.editing.set(product);
        this.notification.success('Photo added.');
        this.imageSaving.set(false);
        this.saved.emit();
      },
      error: () => this.imageSaving.set(false)
    });
  }

  confirmRemoveImage(index: number) {
    const id = this.editingId();
    if (!id) {
      this.pendingImages.update(images => images.filter((_, position) => position !== index));
      return;
    }
    const image = this.editing()?.images?.[index];
    if (!image) {
      return;
    }
    this.confirmation.confirm({
      header: 'Remove photo',
      message: 'This photo will be removed from the product.',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Remove',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.imageSaving.set(true);
        this.productService.removeImage(id, image.id).subscribe({
          next: product => {
            this.editing.set(product);
            this.notification.success('Photo removed.');
            this.imageSaving.set(false);
            this.saved.emit();
          },
          error: () => this.imageSaving.set(false)
        });
      }
    });
  }

  isInvalid(field: string): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }

  errorFor(field: string): string {
    const control = this.form.controls[field];
    if (control.hasError('required')) return 'This field is required.';
    if (control.hasError('min')) return 'Cannot be negative.';
    if (control.hasError('maxlength')) {
      return `Maximum ${control.getError('maxlength').requiredLength} characters.`;
    }
    return '';
  }
}
