import { ConfirmationService } from 'primeng/api';

import { Permission } from '../core/auth/session.model';

/**
 * A step of a document's life — issue, accept, cancel, convert… — described once, so the menu of
 * a list row and the header of the document page offer exactly the same steps, with the same
 * words and the same confirmation.
 */
export interface DocumentAction {
  id: string;
  label: string;
  icon: string;
  /** Who may do it. Comfort only: the backend checks again. */
  permission: Permission;
  severity?: 'secondary' | 'success' | 'danger';
  outlined?: boolean;
  /** Every change of status asks first: it is written to the register, and some cannot be undone. */
  confirm: {
    header: string;
    message: string;
    acceptLabel: string;
    /** For what cannot be taken back: red button, warning icon. */
    danger?: boolean;
  };
}

/** Asks the user to confirm `action`, and runs `accept` only if they do. */
export function confirmDocumentAction(
  confirmation: ConfirmationService, action: DocumentAction, accept: () => void
) {
  const danger = !!action.confirm.danger;
  confirmation.confirm({
    header: action.confirm.header,
    message: action.confirm.message,
    icon: danger ? 'pi pi-exclamation-triangle' : 'pi pi-info-circle',
    acceptLabel: action.confirm.acceptLabel,
    rejectLabel: 'Cancel',
    acceptButtonStyleClass: danger ? 'p-button-danger p-button-sm' : 'p-button-sm',
    rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
    accept
  });
}
