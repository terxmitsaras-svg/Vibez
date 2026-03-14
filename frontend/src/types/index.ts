export type PlatformType = 'spotify' | 'youtube' | 'soundcloud' | 'deezer';

export interface Track {
  title: string;
  artist: string;
  platform: PlatformType;
  trackId: string;
  url: string;
  popularity: number;
  duration: number;
  coverArt?: string;
}

export interface PlaylistResult {
  playlistId: string;
  playlistName: string;
  playlistUrl: string;
  tracks: Track[];
}

export interface PlaylistVariant {
  id: string;
  title: string;
  playlist: PlaylistResult;
}
