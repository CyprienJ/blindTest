import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { LanguageSwitchComponent } from '../../../shared/language-switch/language-switch.component';

@Component({
  selector: 'app-public-home',
  imports: [AsyncPipe, LanguageSwitchComponent, RouterLink],
  templateUrl: './public-home.component.html',
  styleUrl: './public-home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicHomeComponent {
  private readonly authService = inject(AuthService);

  readonly authenticatedUser$ = this.authService.currentUser$.pipe(
    map((user) => (user && this.authService.getAccessToken() ? user : null)),
  );
}
