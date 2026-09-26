import { ConfirmationService } from 'primeng/api';

import { DocumentAction, confirmDocumentAction } from './document-action';

describe('confirmDocumentAction', () => {

  const action = (danger = false): DocumentAction => ({
    id: 'x', label: 'Do it', icon: 'pi pi-check', permission: 'SALE_UPDATE',
    confirm: { header: 'Header', message: 'Sure?', acceptLabel: 'Do it', danger }
  });

  it('asks with the words of the step, and runs it only if the user accepts', () => {
    const confirmation = new ConfirmationService();
    const confirm = spyOn(confirmation, 'confirm');
    let done = 0;

    confirmDocumentAction(confirmation, action(), () => done++);

    const options = confirm.calls.mostRecent().args[0];
    expect(options.header).toBe('Header');
    expect(options.message).toBe('Sure?');
    expect(options.acceptLabel).toBe('Do it');
    expect(options.rejectLabel).toBe('Cancel');
    expect(done).toBe(0);

    options.accept!();
    expect(done).toBe(1);
  });

  it('uses a plain button for an ordinary step and a red one, with a warning icon, for what cannot be undone', () => {
    const confirmation = new ConfirmationService();
    const confirm = spyOn(confirmation, 'confirm');

    confirmDocumentAction(confirmation, action(false), () => undefined);
    expect(confirm.calls.mostRecent().args[0].acceptButtonStyleClass).not.toContain('danger');
    expect(confirm.calls.mostRecent().args[0].icon).toBe('pi pi-info-circle');

    confirmDocumentAction(confirmation, action(true), () => undefined);
    expect(confirm.calls.mostRecent().args[0].acceptButtonStyleClass).toContain('p-button-danger');
    expect(confirm.calls.mostRecent().args[0].icon).toBe('pi pi-exclamation-triangle');
  });
});
