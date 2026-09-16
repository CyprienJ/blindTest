export type TrackStatus = 'PENDING' | 'PUBLISHED' | 'REJECTED';
export type TrackAnswerType = 'TITLE' | 'ARTIST' | 'COMPOSER' | 'ALBUM';

export interface AdminTrack {
  id: string;
  title: string;
  artist: string;
  album: string | null;
  composer: string | null;
  work: string | null;
  category: string | null;
  decade: number | null;
  durationSeconds: number | null;
  excerptStartSeconds: number;
  imageUrl: string | null;
  audioUrl: string | null;
  audioChecksum: string | null;
  audioSizeBytes: number | null;
  youtubeUrl: string | null;
  status: TrackStatus;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface TrackAlias {
  id: string;
  type: TrackAnswerType;
  value: string;
}

export interface TrackUpdate {
  title: string;
  artist: string;
  album: string | null;
  composer: string | null;
  work: string | null;
  category: string | null;
  decade: number | null;
  durationSeconds: number | null;
  excerptStartSeconds: number;
  imageUrl: string | null;
}

export interface MediaPreview {
  url: string;
  expiresAt: string;
}

export interface TrackWaveform {
  durationSeconds: number | null;
  peaks: number[];
}
