import { Component, Input } from '@angular/core';
import { DatePipe, DecimalPipe, registerLocaleData } from '@angular/common';
import localeFrTn from '@angular/common/locales/fr-TN';

import { Address } from '../../../core/models/address.model';
import { amountInWords } from '../amount-in-words';
import {
  PrintOptions, PrintProfile, PrintableDocument, PrintableTax, currencyLabel
} from '../printable-document.model';

// Printed documents are French, with Tunisian number formatting: 1 234,500
registerLocaleData(localeFrTn);

/**
 * The sheet itself: one A4 layout for every kind of document, as Finco prints them — company
 * identity and title on top, the third party, the lines, then the totals with the VAT recap, the
 * amount in words, the notes, terms, bank details and the stamp. Purely presentational: it
 * shows what it is given and computes nothing but the wording of the total.
 */
@Component({
  selector: 'app-printable-document',
  standalone: true,
  imports: [DecimalPipe, DatePipe],
  templateUrl: './printable-document.component.html',
  styleUrl: './printable-document.component.scss'
})
export class PrintableDocumentComponent {

  @Input({ required: true }) document!: PrintableDocument;
  @Input({ required: true }) company!: PrintProfile;
  @Input() options: PrintOptions = { showPrices: true, showReference: true };

  readonly locale = 'fr-TN';

  get currency(): string {
    return currencyLabel(this.company.currency);
  }

  get watermark(): string | null {
    return this.document.state === 'DRAFT' ? 'BROUILLON' : this.document.state === 'CANCELLED' ? 'ANNULÉ' : null;
  }

  get hasDiscount(): boolean {
    return this.document.lines.some(line => line.discountRate > 0);
  }

  get surcharges(): PrintableTax[] {
    return this.document.taxes.filter(tax => tax.kind === 'PERCENTAGE_SURCHARGE');
  }

  get vatRows(): PrintableTax[] {
    return this.document.taxes.filter(tax => tax.kind === 'VAT_RATE');
  }

  get flatCharges(): PrintableTax[] {
    return this.document.taxes.filter(tax => tax.kind === 'FIXED_PER_DOCUMENT');
  }

  /** Shown as one line "Total TVA": the recap table gives the detail per rate. */
  get totalVat(): number {
    return this.vatRows.reduce((sum, row) => sum + row.amount, 0);
  }

  get totalInWords(): string {
    return amountInWords(this.document.total, this.company.currency);
  }

  get companyAddress(): string[] {
    const town = [this.company.postalCode, this.company.city].filter(Boolean).join(' ');
    return [this.company.address, town].filter((line): line is string => !!line);
  }

  /** Phone and e-mail on one line: the header is the costliest part of a sheet's height. */
  get contactLine(): string {
    return [this.company.phone ? `Tél : ${this.company.phone}` : null, this.company.email]
      .filter(Boolean).join(' — ');
  }

  /** The bottom line of the sheet: what a French or Tunisian document must show about its issuer. */
  get legalLine(): string {
    return [
      this.company.name,
      this.company.taxId ? `MF : ${this.company.taxId}` : null,
      this.companyAddress.join(', ') || null,
      this.company.phone ? `Tél : ${this.company.phone}` : null,
      this.company.email
    ].filter(Boolean).join(' — ');
  }

  addressLines(address?: Address | null): string[] {
    if (!address) {
      return [];
    }
    const town = [address.postalCode, address.city].filter(Boolean).join(' ');
    return [address.street, town, address.region, address.country].filter((line): line is string => !!line);
  }

  /** Percentages read "19 %", not "19,000 %". */
  rate(value?: number | null): string {
    return value == null ? '' : `${Number(value)} %`.replace('.', ',');
  }
}
