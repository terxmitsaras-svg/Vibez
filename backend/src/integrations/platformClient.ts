import { ConnectedAccount, Platform, Track, VibeAttributes } from '../models/types.js';

export interface MusicPlatformClient {
  platform: Platform;
  searchTracks(vibe: VibeAttributes, account: ConnectedAccount, limit: number): Promise<Track[]>;
  createPlaylist(name: string, description: string, tracks: Track[], account: ConnectedAccount): Promise<{ id: string; url: string }>;
}
