import { Routes } from '@angular/router';

export const LOBBY_ROUTES: Routes = [
  {
    path: 'join',
    loadComponent: () =>
      import('./join-session/join-session.component').then((m) => m.JoinSessionComponent),
  },
  {
    path: 'create',
    loadComponent: () =>
      import('./create-session/create-session.component').then((m) => m.CreateSessionComponent),
  },
  {
    path: ':code',
    loadComponent: () =>
      import('./lobby-room/lobby-room.component').then((m) => m.LobbyRoomComponent),
  },
  {
    path: '',
    redirectTo: 'join',
    pathMatch: 'full',
  },
];
