export type TrackStatus = 'PENDING' | 'PUBLISHED' | 'REJECTED';
export type TrackAnswerType = 'TITLE' | 'ARTIST' | 'COMPOSER' | 'ALBUM';
export type StreamingPlatform = 'deezer' | 'youtube' | 'spotify' | 'qobuz' | 'tidal' | 'soundcloud';
export type TrackStreamingLinks = Record<StreamingPlatform, string | null>;
export type CoverSource = 'DEEZER' | 'ITUNES' | 'COVER_ART_ARCHIVE' | 'UPLOAD';

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
  /** Length of the game window from the start; rounds loop it. */
  excerptDurationSeconds: number;
  imageUrl: string | null;
  /** Same-origin path of the administrator-selected cover; null when none is stored. */
  coverUrl: string | null;
  coverSource: CoverSource | null;
  /** 1 (easiest) to 5 (hardest); null when not rated yet. */
  difficulty: number | null;
  streamingLinks: TrackStreamingLinks;
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
  excerptDurationSeconds?: number;
  imageUrl: string | null;
  difficulty?: number;
  /** Replaces every stored link when present; null clears a link. */
  streamingLinks?: TrackStreamingLinks;
  /** Cover suggestion or upload to store as the new cover. */
  coverCandidateId?: string;
  /** Clears the stored cover; wins over coverCandidateId. */
  removeCover?: boolean;
}

export interface CoverQuery {
  title: string;
  artist: string;
  album: string | null;
}

/** A cover the administrator can choose; valid for 30 minutes. */
export interface CoverSuggestion {
  candidateId: string;
  source: CoverSource;
  album: string;
  artist: string;
}

export interface MediaPreview {
  url: string;
  expiresAt: string;
}

export interface TrackWaveform {
  durationSeconds: number | null;
  peaks: number[];
}
