import axios from 'axios';
import { ConnectedAccount, Track, VibeAttributes } from '../models/types.js';
import { MusicPlatformClient } from './platformClient.js';

export class SoundCloudClient implements MusicPlatformClient {
  platform = 'soundcloud' as const;

  async searchTracks(vibe: VibeAttributes, account: ConnectedAccount, limit: number): Promise<Track[]> {
    const q = `${vibe.genre} ${vibe.mood}`;
    const { data } = await axios.get('https://api.soundcloud.com/tracks', {
      params: { q, limit, linked_partitioning: 1 },
      headers: { Authorization: `OAuth ${account.accessToken}` }
    });

    return (data.collection ?? []).map((item: any) => ({
      title: item.title,
      artist: item.user?.username ?? 'Unknown',
      platform: 'soundcloud',
      trackId: String(item.id),
      url: item.permalink_url,
      popularity: item.playback_count ?? 0,
      duration: item.duration ?? 0,
      coverArt: item.artwork_url,
      genres: [item.genre || vibe.genre],
      moodTags: [vibe.mood]
    }));
  }

  async createPlaylist(name: string, description: string, tracks: Track[], account: ConnectedAccount): Promise<{ id: string; url: string }> {
    const { data } = await axios.post(
      'https://api.soundcloud.com/playlists',
      {
        playlist: {
          title: name,
          description,
          tracks: tracks.map((track) => ({ id: Number(track.trackId) }))
        }
      },
      { headers: { Authorization: `OAuth ${account.accessToken}` } }
    );

    return { id: String(data.id), url: data.permalink_url };
  }
}
