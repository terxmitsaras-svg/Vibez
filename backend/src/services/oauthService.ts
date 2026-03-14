import axios from 'axios';
import { env } from '../config/env.js';
import { Platform } from '../models/types.js';

interface OAuthTokenResponse {
  accessToken: string;
  refreshToken?: string;
  expiresIn?: number;
}

export function buildAuthUrl(platform: Platform, state: string): string {
  switch (platform) {
    case 'spotify':
      return `https://accounts.spotify.com/authorize?client_id=${env.spotify.clientId}&response_type=code&redirect_uri=${encodeURIComponent(env.spotify.redirectUri)}&scope=${encodeURIComponent('user-read-private playlist-modify-private')}&state=${state}`;
    case 'youtube':
      return `https://accounts.google.com/o/oauth2/v2/auth?client_id=${env.youtube.clientId}&redirect_uri=${encodeURIComponent(env.youtube.redirectUri)}&response_type=code&scope=${encodeURIComponent('https://www.googleapis.com/auth/youtube')}&access_type=offline&state=${state}`;
    case 'soundcloud':
      return `https://secure.soundcloud.com/authorize?client_id=${env.soundcloud.clientId}&redirect_uri=${encodeURIComponent(env.soundcloud.redirectUri)}&response_type=code&scope=non-expiring&state=${state}`;
    case 'deezer':
      return `https://connect.deezer.com/oauth/auth.php?app_id=${env.deezer.appId}&redirect_uri=${encodeURIComponent(env.deezer.redirectUri)}&perms=basic_access,manage_library,delete_library&state=${state}`;
  }
}

export async function exchangeCode(platform: Platform, code: string): Promise<OAuthTokenResponse> {
  switch (platform) {
    case 'spotify': {
      const params = new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        redirect_uri: env.spotify.redirectUri,
        client_id: env.spotify.clientId,
        client_secret: env.spotify.clientSecret
      });
      const { data } = await axios.post('https://accounts.spotify.com/api/token', params);
      return { accessToken: data.access_token, refreshToken: data.refresh_token, expiresIn: data.expires_in };
    }
    case 'youtube': {
      const { data } = await axios.post('https://oauth2.googleapis.com/token', {
        code,
        client_id: env.youtube.clientId,
        client_secret: env.youtube.clientSecret,
        redirect_uri: env.youtube.redirectUri,
        grant_type: 'authorization_code'
      });
      return { accessToken: data.access_token, refreshToken: data.refresh_token, expiresIn: data.expires_in };
    }
    case 'soundcloud': {
      const { data } = await axios.post('https://secure.soundcloud.com/oauth/token', {
        client_id: env.soundcloud.clientId,
        client_secret: env.soundcloud.clientSecret,
        redirect_uri: env.soundcloud.redirectUri,
        grant_type: 'authorization_code',
        code
      });
      return { accessToken: data.access_token, refreshToken: data.refresh_token, expiresIn: data.expires_in };
    }
    case 'deezer': {
      const { data } = await axios.get('https://connect.deezer.com/oauth/access_token.php', {
        params: {
          app_id: env.deezer.appId,
          secret: env.deezer.appSecret,
          code,
          output: 'json'
        }
      });
      return { accessToken: data.access_token, expiresIn: data.expires };
    }
  }
}
