import axios from 'axios';
import { ConnectedAccount, Track, VibeAttributes } from '../models/types.js';
import { MusicPlatformClient } from './platformClient.js';

export class SpotifyClient implements MusicPlatformClient {
  platform = 'spotify' as const;

  async searchTracks(vibe: VibeAttributes, account: ConnectedAccount, limit: number): Promise<Track[]> {
    const query = [vibe.genre, vibe.mood, ...vibe.keywords].join(' ');
    const { data } = await axios.get('https://api.spotify.com/v1/search', {
      params: { q: query, type: 'track', limit },
      headers: { Authorization: `Bearer ${account.accessToken}` }
    });

    return (data.tracks?.items ?? []).map((item: any) => ({
      title: item.name,
      artist: item.artists?.[0]?.name ?? 'Unknown',
      platform: 'spotify',
      trackId: item.id,
      url: item.external_urls?.spotify,
      popularity: item.popularity ?? 0,
      duration: item.duration_ms ?? 0,
      coverArt: item.album?.images?.[0]?.url,
      genres: [vibe.genre],
      moodTags: [vibe.mood]
    }));
  }

  async createPlaylist(name: string, description: string, tracks: Track[], account: ConnectedAccount): Promise<{ id: string; url: string }> {
    const me = await axios.get('https://api.spotify.com/v1/me', {
      headers: { Authorization: `Bearer ${account.accessToken}` }
    });

    const playlist = await axios.post(
      `https://api.spotify.com/v1/users/${me.data.id}/playlists`,
      { name, description, public: false },
      { headers: { Authorization: `Bearer ${account.accessToken}` } }
    );

    const uris = tracks.map((track) => `spotify:track:${track.trackId}`);
    await axios.post(
      `https://api.spotify.com/v1/playlists/${playlist.data.id}/tracks`,
      { uris },
      { headers: { Authorization: `Bearer ${account.accessToken}` } }
    );

    return { id: playlist.data.id, url: playlist.data.external_urls.spotify };
  }
}
