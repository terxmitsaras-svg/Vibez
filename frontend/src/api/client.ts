import axios from 'axios';
import { PlatformType, PlaylistResult, PlaylistVariant } from '../types';

const api = axios.create({
  baseURL: process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:4000'
});

export async function connectPlatform(platform: PlatformType, userId: string): Promise<string> {
  const { data } = await api.get(`/oauth/${platform}/url`, { params: { userId } });
  return data.authUrl;
}

export async function generatePlaylist(payload: {
  userId: string;
  platform: PlatformType;
  vibeText: string;
  targetTrackCount?: number;
}): Promise<PlaylistResult> {
  const { data } = await api.post('/playlists/generate', payload);
  return data;
}

export async function generatePlaylistVariants(payload: {
  userId: string;
  platform: PlatformType;
  vibeText: string;
  targetTrackCount?: number;
}): Promise<PlaylistVariant[]> {
  const { data } = await api.post('/playlists/generate-variants', payload);
  const titles = ['Variant 1', 'Variant 2', 'Variant 3'];
  return (data.variants as PlaylistResult[]).map((playlist, index) => ({
    id: `${playlist.playlistId}-${index}`,
    title: titles[index] ?? `Variant ${index + 1}`,
    playlist
  }));
}
