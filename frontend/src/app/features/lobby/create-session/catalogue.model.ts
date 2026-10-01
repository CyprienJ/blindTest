export interface PublicTrack {
  id: string;
  title: string;
  artist: string;
  album: string | null;
  composer: string | null;
  work: string | null;
  category: string | null;
  decade: number | null;
  durationSeconds: number | null;
  imageUrl: string | null;
  /** 1 (easiest) to 5 (hardest); null when not rated. */
  difficulty: number | null;
}

export interface PublicPlaylist {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  trackCount: number;
}

export interface PublicPlaylistDetail extends PublicPlaylist {
  tracks: PublicTrack[];
}

export interface CataloguePage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
