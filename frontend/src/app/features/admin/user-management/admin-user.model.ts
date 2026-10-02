export type UserRole = 'USER' | 'ADMIN';

export interface AdminUser {
  id: string;
  username: string;
  email: string;
  role: UserRole;
  emailVerified: boolean;
  createdAt: string;
  lastConnexion: string | null;
  gamesPlayed: number;
  playlistCount: number;
}

export interface UserUpdate {
  username: string;
  email: string;
  role: UserRole;
}
