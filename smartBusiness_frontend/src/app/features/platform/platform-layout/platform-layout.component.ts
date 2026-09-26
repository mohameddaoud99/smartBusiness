import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';

import { PlatformAuthService } from '../../../core/platform-auth/platform-auth.service';

/**
 * No sidebar: the platform portal has one real screen today (companies). A second
 * screen can gain a sidebar later without touching this shell's contract.
 */
@Component({
  selector: 'app-platform-layout',
  standalone: true,
  imports: [RouterOutlet, ButtonModule],
  templateUrl: './platform-layout.component.html',
  styleUrl: './platform-layout.component.scss'
})
export class PlatformLayoutComponent {

  readonly auth = inject(PlatformAuthService);

  signOut() {
    this.auth.logout();
  }
}
