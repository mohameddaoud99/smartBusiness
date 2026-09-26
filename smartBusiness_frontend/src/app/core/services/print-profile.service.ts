import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PrintProfile } from '../../features/print/printable-document.model';

@Injectable({ providedIn: 'root' })
export class PrintProfileService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/print-profile`;

  find(): Observable<PrintProfile> {
    return this.http.get<PrintProfile>(this.url);
  }
}
