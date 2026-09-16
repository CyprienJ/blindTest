import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/home/public-home/public-home.component').then(
        (m) => m.PublicHomeComponent,
      ),
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
    path: '',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    loadComponent: () =>
      import('./core/layout/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: 'home',
        loadComponent: () =>
          import('./features/home/authenticated-home/authenticated-home.component').then(
            (m) => m.AuthenticatedHomeComponent,
          ),
      },
      {
        path: 'lobby',
        loadChildren: () => import('./features/lobby/lobby.routes').then((m) => m.LOBBY_ROUTES),
      },
      {
        path: 'game',
        loadChildren: () => import('./features/game/game.routes').then((m) => m.GAME_ROUTES),
      },
      {
        path: 'profile',
        loadChildren: () =>
          import('./features/profile/profile.routes').then((m) => m.PROFILE_ROUTES),
      },
      {
        path: 'friends',
        loadChildren: () =>
          import('./features/friends/friends.routes').then((m) => m.FRIENDS_ROUTES),
      },
      {
        path: 'end-screen',
        loadChildren: () =>
          import('./features/end-screen/end-screen.routes').then((m) => m.END_SCREEN_ROUTES),
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
    ],
  },
  {
    path: '**',
    redirectTo: 'auth/login',
  },
];
