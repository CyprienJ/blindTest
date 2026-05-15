export interface AdminTrack {
  id: string;
  title: string;
  artist: string;
  composer: string | null;
  work: string | null;
  category: string | null;
  decade: number | null;
  durationSeconds: number | null;
  imageUrl: string | null;
  audioUrl: string;
  youtubeUrl: string | null;
  status: 'PENDING' | 'PUBLISHED' | 'REJECTED';
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ImportStartResponse {
  jobId: string;
}

export interface ImportJob {
  jobId: string;
  status: 'QUEUED' | 'DOWNLOADING' | 'PROCESSING' | 'DONE' | 'FAILED';
  progress: number;
  errorMessage: string | null;
  createdAt: string;
  track: AdminTrack | null;
}

export interface WsImportProgressEvent {
  jobId: string;
  status: string;
  progress: number;
}

export interface WsImportDoneEvent {
  jobId: string;
  trackId: string;
  previewUrl: string;
}

export interface WsImportFailedEvent {
  jobId: string;
  errorMessage: string;
}

export type WsImportEvent = WsImportProgressEvent | WsImportDoneEvent | WsImportFailedEvent;

export interface AdminPlaylist {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  trackCount: number;
}

export interface AdminPlaylistDetail extends AdminPlaylist {
  tracks: AdminTrack[];
}

export interface AdminUser {
  id: string;
  username: string;
  email: string;
  role: 'USER' | 'ADMIN';
  emailVerified: boolean;
  avatarUrl: string | null;
  createdAt: string;
}

export interface ImportTrackPayload {
  youtubeUrl: string;
  title: string;
  artist: string;
  composer?: string;
  work?: string;
  category?: string;
  decade?: number | null;
}
