import { Router } from '@angular/router';

/**
 * Opens the print preview of a document in a new tab — a tab, so the user keeps the screen they
 * came from and can close the sheet when done. `family` is which side the document belongs to.
 */
export function openPrintPage(router: Router, family: 'sales' | 'purchases', id: number) {
  const url = router.serializeUrl(router.createUrlTree(['/print', family, id]));
  window.open(url, '_blank');
}
