import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { DrawerModule } from 'primeng/drawer';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { TimelineModule } from 'primeng/timeline';
import { SkeletonModule } from 'primeng/skeleton';

import { UserService } from '../../../core/services/user.service';
import { UserResponse, statusLabel, statusSeverity } from '../user.model';
import { AuditLogResponse, auditColor, auditIcon } from '../../audit/audit.model';

@Component({
  selector: 'app-user-detail',
  standalone: true,
  imports: [
    DatePipe, DrawerModule, TabsModule, TagModule,
    ButtonModule, TimelineModule, SkeletonModule
  ],
  templateUrl: './user-detail.component.html',
  styleUrl: './user-detail.component.scss'
})
export class UserDetailComponent {

  private readonly userService = inject(UserService);

  @Input() visible = false;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() edit = new EventEmitter<UserResponse>();

  readonly user = signal<UserResponse | null>(null);
  readonly history = signal<AuditLogResponse[]>([]);
  readonly loadingHistory = signal(false);

  readonly statusLabel = statusLabel;
  readonly statusSeverity = statusSeverity;

  /** Same icons and colours as the audit screen — one vocabulary across the app. */
  readonly auditIcon = auditIcon;
  readonly auditColor = auditColor;

  open(user: UserResponse) {
    this.user.set(user);
    this.history.set([]);
    this.visible = true;
    this.visibleChange.emit(true);
    this.loadHistory(user.id);
  }

  close() {
    this.visible = false;
    this.visibleChange.emit(false);
  }

  initials(user: UserResponse): string {
    return (user.firstName.charAt(0) + user.lastName.charAt(0)).toUpperCase();
  }

  private loadHistory(id: number) {
    this.loadingHistory.set(true);
    this.userService.findHistory(id).subscribe({
      next: history => {
        this.history.set(history);
        this.loadingHistory.set(false);
      },
      error: () => this.loadingHistory.set(false)
    });
  }
}
