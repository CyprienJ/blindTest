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
  imageUrl: string | null;
}
