import { Routes } from '@angular/router';

export const GAME_ROUTES: Routes = [
  {
    path: ':code',
    loadComponent: () => import('./game-room/game-room.component').then((m) => m.GameRoomComponent),
  },
  {
    path: '',
    redirectTo: '/lobby/join',
    pathMatch: 'full',
  },
];
