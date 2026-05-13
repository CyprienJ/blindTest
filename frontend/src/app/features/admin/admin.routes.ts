import { Routes } from '@angular/router';

export const ADMIN_ROUTES: Routes = [
  {
    path: 'tracks',
    loadComponent: () =>
      import('./track-list/track-list.component').then((m) => m.TrackListComponent),
  },
  {
    path: 'tracks/import',
    loadComponent: () =>
      import('./track-import/track-import.component').then((m) => m.TrackImportComponent),
  },
  {
    path: 'playlists',
    loadComponent: () =>
      import('./playlist-editor/playlist-editor.component').then((m) => m.PlaylistEditorComponent),
  },
  {
    path: 'users',
    loadComponent: () =>
      import('./user-management/user-management.component').then((m) => m.UserManagementComponent),
  },
  {
    path: '',
    redirectTo: 'tracks',
    pathMatch: 'full',
  },
];
