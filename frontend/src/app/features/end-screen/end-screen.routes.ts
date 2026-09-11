import { Routes } from '@angular/router';

export const END_SCREEN_ROUTES: Routes = [
  {
    path: ':sessionId',
    loadComponent: () => import('./end-screen.component').then((m) => m.EndScreenComponent),
  },
  {
    path: '',
    redirectTo: '/lobby/join',
    pathMatch: 'full',
  },
];
