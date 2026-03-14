import axios from 'axios';
import { ConnectedAccount, Track, VibeAttributes } from '../models/types.js';
import { MusicPlatformClient } from './platformClient.js';

export class YouTubeClient implements MusicPlatformClient {
  platform = 'youtube' as const;

  async searchTracks(vibe: VibeAttributes, account: ConnectedAccount, limit: number): Promise<Track[]> {
    const q = `${vibe.genre} ${vibe.mood} music`;
    const { data } = await axios.get('https://www.googleapis.com/youtube/v3/search', {
      params: { part: 'snippet', q, maxResults: limit, type: 'video' },
      headers: { Authorization: `Bearer ${account.accessToken}` }
    });

    return (data.items ?? []).map((item: any) => ({
      title: item.snippet.title,
      artist: item.snippet.channelTitle,
      platform: 'youtube',
      trackId: item.id.videoId,
      url: `https://www.youtube.com/watch?v=${item.id.videoId}`,
      popularity: 50,
      duration: 0,
      coverArt: item.snippet.thumbnails?.high?.url,
      genres: [vibe.genre],
      moodTags: [vibe.mood]
    }));
  }

  async createPlaylist(name: string, description: string, tracks: Track[], account: ConnectedAccount): Promise<{ id: string; url: string }> {
    const { data: playlist } = await axios.post(
      'https://www.googleapis.com/youtube/v3/playlists?part=snippet,status',
      {
        snippet: { title: name, description },
        status: { privacyStatus: 'private' }
      },
      { headers: { Authorization: `Bearer ${account.accessToken}` } }
    );

    for (const track of tracks) {
      await axios.post(
        'https://www.googleapis.com/youtube/v3/playlistItems?part=snippet',
        {
          snippet: {
            playlistId: playlist.id,
            resourceId: { kind: 'youtube#video', videoId: track.trackId }
          }
        },
        { headers: { Authorization: `Bearer ${account.accessToken}` } }
      );
    }

    return { id: playlist.id, url: `https://www.youtube.com/playlist?list=${playlist.id}` };
  }
}
