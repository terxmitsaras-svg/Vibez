import axios from 'axios';
import { ConnectedAccount, Track, VibeAttributes } from '../models/types.js';
import { MusicPlatformClient } from './platformClient.js';

export class DeezerClient implements MusicPlatformClient {
  platform = 'deezer' as const;

  async searchTracks(vibe: VibeAttributes, _account: ConnectedAccount, limit: number): Promise<Track[]> {
    const q = `${vibe.genre} ${vibe.mood}`;
    const { data } = await axios.get('https://api.deezer.com/search', { params: { q, limit } });

    return (data.data ?? []).map((item: any) => ({
      title: item.title,
      artist: item.artist?.name ?? 'Unknown',
      platform: 'deezer',
      trackId: String(item.id),
      url: item.link,
      popularity: item.rank ?? 0,
      duration: item.duration ? item.duration * 1000 : 0,
      coverArt: item.album?.cover_medium,
      genres: [vibe.genre],
      moodTags: [vibe.mood]
    }));
  }

  async createPlaylist(name: string, _description: string, tracks: Track[], account: ConnectedAccount): Promise<{ id: string; url: string }> {
    const { data: me } = await axios.get('https://api.deezer.com/user/me', {
      params: { access_token: account.accessToken }
    });

    const { data: created } = await axios.post('https://api.deezer.com/user/me/playlists', null, {
      params: { access_token: account.accessToken, title: name }
    });

    await axios.post(`https://api.deezer.com/playlist/${created.id}/tracks`, null, {
      params: {
        access_token: account.accessToken,
        songs: tracks.map((track) => track.trackId).join(',')
      }
    });

    return { id: String(created.id), url: `https://www.deezer.com/profile/${me.id}/playlists/${created.id}` };
  }
}
