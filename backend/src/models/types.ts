export type Platform = 'spotify' | 'youtube' | 'soundcloud' | 'deezer';

export interface VibeAttributes {
  genre: string;
  mood: string;
  energy: number;
  danceability: number;
  tempoRange: [number, number];
  instrumentFocus: string[];
  keywords: string[];
}

export interface Track {
  title: string;
  artist: string;
  platform: Platform;
  trackId: string;
  url: string;
  popularity: number;
  duration: number;
  genres?: string[];
  energy?: number;
  tempo?: number;
  moodTags?: string[];
  coverArt?: string;
}

export interface PlaylistRequest {
  userId: string;
  platform: Platform;
  vibeText: string;
  targetTrackCount?: number;
}

export interface PlaylistResult {
  playlistId: string;
  playlistName: string;
  playlistUrl: string;
  tracks: Track[];
}

export interface ConnectedAccount {
  userId: string;
  platform: Platform;
  accessToken: string;
  refreshToken?: string;
  expiresAt?: Date;
}
