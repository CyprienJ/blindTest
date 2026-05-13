import { Routes } from '@angular/router';

export const FRIENDS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./friend-list/friend-list.component').then((m) => m.FriendListComponent),
  },
  {
    path: 'requests',
    loadComponent: () =>
      import('./friend-requests/friend-requests.component').then((m) => m.FriendRequestsComponent),
  },
];
