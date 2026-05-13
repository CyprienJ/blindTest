import { Routes } from '@angular/router';

export const PROFILE_ROUTES: Routes = [
  {
    path: 'me',
    loadComponent: () =>
      import('./my-profile/my-profile.component').then((m) => m.MyProfileComponent),
  },
  {
    path: 'history',
    loadComponent: () =>
      import('./game-history/game-history.component').then((m) => m.GameHistoryComponent),
  },
  {
    path: '',
    redirectTo: 'me',
    pathMatch: 'full',
  },
];
