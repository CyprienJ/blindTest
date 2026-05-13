import { Routes } from '@angular/router';

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
    path: 'lobby',
    loadChildren: () => import('./features/lobby/lobby.routes').then((m) => m.LOBBY_ROUTES),
  },
  {
    path: 'game',
    loadChildren: () => import('./features/game/game.routes').then((m) => m.GAME_ROUTES),
  },
  {
    path: 'profile',
    loadChildren: () => import('./features/profile/profile.routes').then((m) => m.PROFILE_ROUTES),
  },
  {
    path: 'friends',
    loadChildren: () => import('./features/friends/friends.routes').then((m) => m.FRIENDS_ROUTES),
  },
  {
    path: 'end-screen',
    loadChildren: () =>
      import('./features/end-screen/end-screen.routes').then((m) => m.END_SCREEN_ROUTES),
  },
  {
    path: 'admin',
    loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
  {
    path: '**',
    redirectTo: 'auth/login',
  },
];
