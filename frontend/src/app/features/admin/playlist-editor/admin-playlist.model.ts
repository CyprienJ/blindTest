import { AdminTrack } from '../track-list/admin-track.model';

export interface AdminPlaylistSummary {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  trackCount: number;
}

export interface AdminPlaylistDetail extends AdminPlaylistSummary {
  tracks: AdminTrack[];
}

export interface PlaylistMetadata {
  name: string;
  description: string | null;
  /** Uploaded cover candidate to store as the new cover. */
  coverCandidateId?: string;
  /** When true, clears the stored cover; takes precedence over coverCandidateId. */
  removeCover?: boolean;
}
