import { TestBed } from '@angular/core/testing';

import { PdfExportService } from './pdf-export.service';

/** A real PDF is built here, in headless Chrome — the library is the point of this service. */
describe('PdfExportService', () => {

  let service: PdfExportService;
  let sheet: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PdfExportService);

    sheet = document.createElement('div');
    sheet.style.cssText = 'width: 600px; padding: 20px; font-family: Arial;';
    sheet.innerHTML = '<h1>DEVIS</h1><p>Net à payer 37,057 DT</p>';
    document.body.appendChild(sheet);
  });

  afterEach(() => sheet.remove());

  it('builds a PDF from an element', async () => {
    const blob = await service.toBlob(sheet);

    expect(blob.type).toBe('application/pdf');
    expect(blob.size).toBeGreaterThan(1000);
    // every PDF starts with its own signature
    expect(await blob.slice(0, 5).text()).toBe('%PDF-');
  }, 30000);

  it('hands the file to the browser under the name it was given', async () => {
    const clicks: { name: string; href: string }[] = [];
    spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
      clicks.push({ name: this.download, href: this.href });
    });

    await service.download(sheet, 'DEVIS-2026-0002 - DEVIS.pdf');

    expect(clicks.length).toBe(1);
    expect(clicks[0].name).toBe('DEVIS-2026-0002 - DEVIS.pdf');
    expect(clicks[0].href).toMatch(/^blob:/);
  }, 30000);
});
