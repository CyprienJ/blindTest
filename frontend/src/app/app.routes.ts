import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'auth/login',
    pathMatch: 'full',
  },
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },
  {
    path: 'verify-email',
    loadComponent: () =>
      import('./features/auth/verify-email/verify-email.component').then(
        (m) => m.VerifyEmailComponent,
      ),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password.component').then(
        (m) => m.ResetPasswordComponent,
      ),
  },
  {
    path: 'lobby',
    canActivate: [authGuard],
    loadChildren: () => import('./features/lobby/lobby.routes').then((m) => m.LOBBY_ROUTES),
  },
  {
    path: 'game',
    canActivate: [authGuard],
    loadChildren: () => import('./features/game/game.routes').then((m) => m.GAME_ROUTES),
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadChildren: () => import('./features/profile/profile.routes').then((m) => m.PROFILE_ROUTES),
  },
  {
    path: 'friends',
    canActivate: [authGuard],
    loadChildren: () => import('./features/friends/friends.routes').then((m) => m.FRIENDS_ROUTES),
  },
  {
    path: 'end-screen',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/end-screen/end-screen.routes').then((m) => m.END_SCREEN_ROUTES),
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
  {
    path: '**',
    redirectTo: 'auth/login',
  },
];
