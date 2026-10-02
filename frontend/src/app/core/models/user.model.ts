export interface UserSummary {
  id: string;
  username: string;
  email: string;
  role: string;
  avatarUrl: string | null;
}

export interface LoginResponse {
  accessToken: string;
  user: UserSummary;
}

export interface RefreshResponse {
  accessToken: string;
}

export interface RegisterResponse {
  id: string;
  username: string;
  email: string;
}

export interface InvitationLookupResponse {
  email: string;
  expiresAt: string;
}
