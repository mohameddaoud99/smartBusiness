import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  NumberingSequenceRequest, NumberingSequenceResponse
} from '../../features/settings/numbering/numbering.model';

@Injectable({ providedIn: 'root' })
export class NumberingService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/settings/numbering`;

  findAll(): Observable<NumberingSequenceResponse[]> {
    return this.http.get<NumberingSequenceResponse[]>(this.url);
  }

  update(documentType: string, request: NumberingSequenceRequest): Observable<NumberingSequenceResponse> {
    return this.http.put<NumberingSequenceResponse>(`${this.url}/${documentType}`, request);
  }
}
