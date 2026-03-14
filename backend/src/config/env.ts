import dotenv from 'dotenv';

dotenv.config();

const required = ['OPENAI_API_KEY', 'DATABASE_URL'];
for (const key of required) {
  if (!process.env[key]) {
    console.warn(`Missing required env var: ${key}`);
  }
}

export const env = {
  port: Number(process.env.PORT ?? 4000),
  openAiKey: process.env.OPENAI_API_KEY ?? '',
  openAiModel: process.env.OPENAI_MODEL ?? 'gpt-4o-mini',
  databaseUrl: process.env.DATABASE_URL ?? '',
  frontendCallbackUrl: process.env.FRONTEND_CALLBACK_URL ?? 'vibez://oauth/callback',
  spotify: {
    clientId: process.env.SPOTIFY_CLIENT_ID ?? '',
    clientSecret: process.env.SPOTIFY_CLIENT_SECRET ?? '',
    redirectUri: process.env.SPOTIFY_REDIRECT_URI ?? ''
  },
  youtube: {
    clientId: process.env.YOUTUBE_CLIENT_ID ?? '',
    clientSecret: process.env.YOUTUBE_CLIENT_SECRET ?? '',
    redirectUri: process.env.YOUTUBE_REDIRECT_URI ?? ''
  },
  soundcloud: {
    clientId: process.env.SOUNDCLOUD_CLIENT_ID ?? '',
    clientSecret: process.env.SOUNDCLOUD_CLIENT_SECRET ?? '',
    redirectUri: process.env.SOUNDCLOUD_REDIRECT_URI ?? ''
  },
  deezer: {
    appId: process.env.DEEZER_APP_ID ?? '',
    appSecret: process.env.DEEZER_APP_SECRET ?? '',
    redirectUri: process.env.DEEZER_REDIRECT_URI ?? ''
  }
};
