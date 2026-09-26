import { Injectable } from '@angular/core';

/**
 * Turns an element into a PDF file, in the browser. html2pdf.js is loaded on first use — it is
 * a large library and most visits never download anything — and works by photographing the
 * element page by page, so the file is exactly the sheet the user saw, at the cost of the text
 * not being selectable.
 */
@Injectable({ providedIn: 'root' })
export class PdfExportService {

  /** A4 with the same 12 mm margin the print dialog uses (`@page` in styles.scss). */
  private static readonly MARGIN_MM = 12;

  async toBlob(element: HTMLElement): Promise<Blob> {
    const { default: html2pdf } = await import('html2pdf.js');

    const options = {
      margin: PdfExportService.MARGIN_MM,
      image: { type: 'jpeg', quality: 0.98 },
      html2canvas: { scale: 2, useCORS: true, backgroundColor: '#ffffff' },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
      // Never cut a table row, the totals or the closing block across two pages
      pagebreak: { mode: ['css', 'legacy'], avoid: ['tr', '.totals-area', '.in-words', '.closing', '.party'] }
    };
    // `pagebreak` is honoured by the library but missing from its typings
    return html2pdf().set(options as never).from(element).outputPdf('blob');
  }

  /** Builds the PDF and hands it to the browser as a download named `filename`. */
  async download(element: HTMLElement, filename: string): Promise<void> {
    const blob = await this.toBlob(element);

    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    // Revoked a moment later: some browsers start reading the file after the click returns
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  }
}
