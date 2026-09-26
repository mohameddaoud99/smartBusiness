import { TestBed, ComponentFixture } from '@angular/core/testing';

import { PrintableDocumentComponent } from './printable-document.component';
import { PrintProfile, PrintableDocument } from '../printable-document.model';

/**
 * The Finco reference case (§07): 30,000 HT, FODEC 1 % = 0,300, VAT 19 % on 30,300 = 5,757,
 * stamp 1,000 → net à payer 37,057 — and the sheet must say it the way Finco prints it.
 */
describe('PrintableDocumentComponent', () => {

  let fixture: ComponentFixture<PrintableDocumentComponent>;
  let component: PrintableDocumentComponent;

  const company: PrintProfile = {
    name: 'ABC Distribution', email: 'contact@abc.tn', phone: '+216 71 000 000',
    address: '12 Rue de Carthage', postalCode: '1002', city: 'Tunis', taxId: '1234567A/A/M/000',
    currency: 'TND', logoDataUri: 'data:image/png;base64,AA==', stampDataUri: 'data:image/png;base64,AQ==',
    bankAccounts: [{ label: 'Main', bankName: 'BIAT', rib: '07000000000000000001', currency: 'TND' }]
  };

  function document(overrides: Partial<PrintableDocument> = {}): PrintableDocument {
    return {
      title: 'DEVIS', reference: 'QUO-2026-00002', state: 'ACTIVE',
      issueDate: '2026-09-20', dueDate: '2026-10-20', dueDateLabel: 'Valable jusqu\'au',
      partyLabel: 'Client', warehouseLabel: '', netLabel: 'Net à payer',
      party: {
        name: 'Client Alpha', taxId: '7654321B/A/M/000', phone: '+216 20 000 000', email: 'alpha@client.tn',
        billingAddress: { street: '5 Avenue Habib Bourguiba', postalCode: '3000', city: 'Sfax', country: 'Tunisie' },
        shippingAddress: { street: 'Zone industrielle', city: 'Sousse' }
      },
      lines: [
        { reference: 'pg 001', designation: 'pantalon gucci', quantity: 1, unitPrice: 30, discountRate: 0, vatRate: 19, lineTotal: 30 },
        { reference: null, designation: 'pantlon', quantity: 5, unitPrice: 0, discountRate: 0, vatRate: 19, lineTotal: 0 }
      ],
      taxes: [
        { kind: 'PERCENTAGE_SURCHARGE', name: 'FODEC', rate: 1, base: 30, amount: 0.3 },
        { kind: 'VAT_RATE', name: 'VAT 19%', rate: 19, base: 30.3, amount: 5.757 },
        { kind: 'FIXED_PER_DOCUMENT', name: 'Timbre fiscal', amount: 1 }
      ],
      subtotal: 30, total: 37.057,
      notes: 'Merci de votre confiance', terms: 'Paiement à 30 jours',
      totalPhrase: 'Arrêté le présent devis à la somme de',
      ...overrides
    };
  }

  function render(doc: PrintableDocument = document(), options = { showPrices: true, showReference: true }, profile = company) {
    fixture = TestBed.createComponent(PrintableDocumentComponent);
    component = fixture.componentInstance;
    component.document = doc;
    component.company = profile;
    component.options = options;
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(root: HTMLElement, selector: string): string {
    // innerText, not textContent: it puts a space between table cells and grid items, as the eye sees them
    return ((root.querySelector(selector) as HTMLElement | null)?.innerText ?? '').replace(/\s+/g, ' ').trim();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PrintableDocumentComponent] }).compileComponents();
  });

  // ----- Header -----

  it('shows the issuer: logo, name, address, phone, email and matricule fiscal', () => {
    const root = render();

    const header = text(root, '.issuer');
    expect(header).toContain('ABC Distribution');
    expect(header).toContain('12 Rue de Carthage');
    expect(header).toContain('1002 Tunis');
    expect(header).toContain('Tél : +216 71 000 000');
    expect(header).toContain('contact@abc.tn');
    expect(header).toContain('MF : 1234567A/A/M/000');
    expect(root.querySelector('.logo')).not.toBeNull();
  });

  it('titles the document with its number and dates', () => {
    const root = render();

    expect(text(root, '.doc-title')).toBe('DEVIS');
    expect(text(root, '.doc-reference')).toBe('N° QUO-2026-00002');
    expect(text(root, '.doc-dates')).toContain('Date d\'émission 20/09/2026');
    expect(text(root, '.doc-dates')).toContain('Valable jusqu\'au 20/10/2026');
  });

  it('leaves the due date out when there is none', () => {
    const root = render(document({ dueDate: null }));

    expect(text(root, '.doc-dates')).not.toContain('Valable');
  });

  it('shows a draft as not numbered yet', () => {
    const root = render(document({ reference: null, state: 'DRAFT' }));

    expect(text(root, '.doc-reference')).toBe('Brouillon — non numéroté');
  });

  // ----- Parties -----

  it('shows the customer with matricule, billing address, phone and email, and the shipping address apart', () => {
    const root = render();

    const parties = root.querySelectorAll('.party');
    expect(parties.length).toBe(2);
    const customer = (parties[0].textContent ?? '').replace(/\s+/g, ' ');
    expect(customer).toContain('Client');
    expect(customer).toContain('Client Alpha');
    expect(customer).toContain('MF : 7654321B/A/M/000');
    expect(customer).toContain('5 Avenue Habib Bourguiba');
    expect(customer).toContain('3000 Sfax');
    expect(customer).toContain('Tunisie');
    expect(customer).toContain('alpha@client.tn');
    expect((parties[1].textContent ?? '')).toContain('Adresse de livraison');
    expect((parties[1].textContent ?? '')).toContain('Zone industrielle');
  });

  it('shows the CIN of an individual, and no shipping block when there is no shipping address', () => {
    const root = render(document({ party: { name: 'Karim', nationalId: '01234567' } }));

    expect(text(root, '.party')).toContain('CIN : 01234567');
    expect(root.querySelectorAll('.party').length).toBe(1);
  });

  it('prints the label of the net total the wording gives: a credit note deducts', () => {
    const root = render(document({ title: 'AVOIR', netLabel: 'Net à déduire' }));

    expect(text(root, '.totals')).toContain('Net à déduire 37,057 DT');
    expect(text(root, '.totals')).not.toContain('Net à payer');
  });

  it('shows no warehouse block for a document whose wording names none', () => {
    const root = render(document({ warehouseName: 'Entrepôt Sfax', warehouseLabel: '' }));

    expect(root.textContent).not.toContain('Entrepôt Sfax');
  });

  it('shows the warehouse of a goods receipt', () => {
    const root = render(document({ partyLabel: 'Fournisseur', warehouseLabel: 'Entrepôt de réception', warehouseName: 'Entrepôt Sfax' }));

    expect(root.textContent).toContain('Entrepôt de réception');
    expect(root.textContent).toContain('Entrepôt Sfax');
  });

  // ----- Lines -----

  it('lists the lines with number, reference, designation, quantity, unit price, VAT and total in French format', () => {
    const root = render();

    const rows = root.querySelectorAll('.lines tbody tr');
    expect(rows.length).toBe(2);
    const first = (rows[0].textContent ?? '').replace(/\s+/g, ' ');
    expect(first).toContain('1');
    expect(first).toContain('pg 001');
    expect(first).toContain('pantalon gucci');
    expect(first).toContain('30,000');
    expect(first).toContain('19 %');
    expect((rows[1].textContent ?? '')).toContain('—'); // a free line has no reference
    expect((rows[1].textContent ?? '')).toContain('0,000');
  });

  it('shows the discount column only when a line carries a discount', () => {
    expect(render().querySelector('.lines thead')!.textContent).not.toContain('Remise');

    fixture.destroy();
    const withDiscount = document();
    withDiscount.lines[0].discountRate = 10;
    const root = render(withDiscount);

    expect(root.querySelector('.lines thead')!.textContent).toContain('Remise');
    expect(root.querySelector('.lines tbody')!.textContent).toContain('10 %');
  });

  it('can hide the reference column', () => {
    const root = render(document(), { showPrices: true, showReference: false });

    expect(root.querySelector('.lines thead')!.textContent).not.toContain('Réf.');
    expect(root.querySelector('.lines tbody')!.textContent).not.toContain('pg 001');
  });

  // ----- Totals -----

  it('prints the totals of the Finco reference case: HT, FODEC, TVA, timbre, net à payer', () => {
    const root = render();

    const totals = text(root, '.totals');
    expect(totals).toContain('Total HT 30,000 DT');
    expect(totals).toContain('FODEC 1 % 0,300 DT');
    expect(totals).toContain('Total TVA 5,757 DT');
    expect(totals).toContain('Timbre fiscal 1,000 DT');
    expect(totals).toContain('Net à payer 37,057 DT');
  });

  it('prints the VAT recap with the base including the FODEC', () => {
    const root = render();

    const recap = text(root, '.vat-recap');
    expect(recap).toContain('Taux TVA');
    expect(recap).toContain('19 %');
    expect(recap).toContain('30,300');
    expect(recap).toContain('5,757');
  });

  it('spells the total out with the legal sentence', () => {
    const root = render();

    expect(text(root, '.in-words'))
      .toBe('Arrêté le présent devis à la somme de : trente-sept dinars et cinquante-sept millimes.');
  });

  it('shows the currency code for a currency other than the dinar', () => {
    const root = render(document(), { showPrices: true, showReference: true }, { ...company, currency: 'EUR' });

    expect(text(root, '.totals')).toContain('37,057 EUR');
    expect(text(root, '.in-words')).toContain('euros');
  });

  it('shows no amount at all when prices are hidden', () => {
    const root = render(document(), { showPrices: false, showReference: true });

    expect(root.querySelector('.totals')).toBeNull();
    expect(root.querySelector('.vat-recap')).toBeNull();
    expect(root.querySelector('.in-words')).toBeNull();
    expect(root.querySelector('.lines thead')!.textContent).not.toContain('P.U HT');
    expect(root.querySelector('.lines tbody')!.textContent).not.toContain('30,000');
  });

  // ----- Closing -----

  it('prints the notes, the terms, the bank details, the stamp and the legal footer', () => {
    const root = render();

    // the headings are shown in capitals by the stylesheet, which innerText reflects
    const closing = text(root, '.closing');
    expect(closing).toContain('Merci de votre confiance');
    expect(closing.toLowerCase()).toContain('conditions générales');
    expect(closing).toContain('Paiement à 30 jours');
    expect(closing).toContain('BIAT — RIB : 07000000000000000001');
    expect(root.querySelector('.stamp')).not.toBeNull();
    expect(text(root, '.sheet-footer')).toContain('ABC Distribution — MF : 1234567A/A/M/000');
  });

  it('leaves out what the company has not set: logo, stamp, bank details', () => {
    const root = render(document({ notes: null, terms: null }),
      { showPrices: true, showReference: true },
      { ...company, logoDataUri: null, stampDataUri: null, bankAccounts: [] });

    expect(root.querySelector('.logo')).toBeNull();
    expect(root.querySelector('.stamp')).toBeNull();
    expect(text(root, '.closing').toLowerCase()).not.toContain('coordonnées bancaires');
    expect(text(root, '.closing').toLowerCase()).not.toContain('notes');
  });

  // ----- Watermark -----

  it('watermarks a draft and a cancelled document, never a valid one', () => {
    expect(text(render(document({ state: 'DRAFT' })), '.watermark')).toBe('BROUILLON');
    fixture.destroy();
    expect(text(render(document({ state: 'CANCELLED' })), '.watermark')).toBe('ANNULÉ');
    fixture.destroy();
    expect(render(document({ state: 'ACTIVE' })).querySelector('.watermark')).toBeNull();
  });
});
