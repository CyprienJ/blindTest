import { AdminTrack, TrackAnswerType } from '../track-list/admin-track.model';

export type ImportJobStatus =
  | 'QUEUED'
  | 'DOWNLOADING'
  | 'PROCESSING'
  | 'DONE'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'FAILED';

export interface ImportTrackRequest {
  youtubeUrl: string;
}

export interface ImportStartResponse {
  jobId: string;
}

export interface ImportJob {
  jobId: string;
  status: ImportJobStatus;
  progress: number;
  errorMessage: string | null;
  previewAvailable: boolean;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  track: AdminTrack | null;
}

export interface ImportAliasDraft {
  type: TrackAnswerType;
  value: string;
}

export type ImportRealtimeEvent =
  | { kind: 'progress'; jobId: string; status: ImportJobStatus; progress: number }
  | { kind: 'done'; jobId: string; trackId: string }
  | { kind: 'failed'; jobId: string; errorMessage: string };

export type RealtimeConnectionState = 'connecting' | 'connected' | 'offline';
