import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-authenticated-home',
  imports: [AsyncPipe, RouterLink],
  templateUrl: './authenticated-home.component.html',
  styleUrl: './authenticated-home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthenticatedHomeComponent {
  private readonly authService = inject(AuthService);

  readonly currentUser$ = this.authService.currentUser$;
}
