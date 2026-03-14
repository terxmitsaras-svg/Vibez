# Vibez MVP

Vibez is an AI-powered playlist generator. Users describe a vibe in natural language and Vibez creates a playlist in Spotify, YouTube, SoundCloud, or Deezer.

## Project structure

- `backend/`: Node.js + Express API (AI interpretation, OAuth, playlist generation, platform integrations, PostgreSQL persistence)
- `frontend/`: React Native (Expo) mobile UI for input and playlist preview
- `android-kotlin/`: Kotlin Android MVP client using Jetpack Compose
- `.env.example`: required environment variables

## What has been built so far

- AI-assisted playlist generation backend with OAuth entry points and platform connectors for Spotify, YouTube, SoundCloud, and Deezer
- React Native app with minimal home banner, platform connect actions, dark/light mode, and 3 generated playlist variants
- Kotlin Android Compose MVP with a vibe input flow and playlist preview screen
- PostgreSQL schema + persistence layer for users, connected accounts, playlists, and tracks
- Build commands wired for backend/frontend TypeScript checks and Android project verification in this environment

## Backend setup

1. Install dependencies:
   ```bash
   cd backend
   npm install
   ```
2. Copy env file and fill API credentials:
   ```bash
   cp ../.env.example .env
   ```
3. Create database schema:
   ```bash
   psql "$DATABASE_URL" -f sql/schema.sql
   ```
4. Run API:
   ```bash
   npm run dev
   ```

### Backend endpoints

- `GET /health`
- `GET /oauth/:platform/url?userId=<uuid>`
- `POST /oauth/:platform/callback`
- `POST /playlists/generate`
- `POST /playlists/generate-variants` (returns 3 playlist variants)

### Playlist generation pipeline

1. Receive vibe text
2. Interpret vibe via OpenAI to structured attributes
3. Search tracks from connected platform API
4. Rank by genre, mood, tempo, energy, popularity
5. Pick top 20-40 tracks
6. Create playlist in user account and add tracks
7. Persist playlist metadata and tracks in PostgreSQL

## React Native app setup

```bash
cd frontend
npm install
npm run start
```

UI flow implemented:

- Minimalistic home screen with Vibez banner (top-left), menu icon, dark/light switch
- Red / mustard yellow / white palette in dark and light modes
- Platform connect action + single query bar for mood/genre/artist/moment
- Generate action creates 3 playlist variants
- Preview screen shows three variant cards with quick open links

## Kotlin Android app setup

Open `android-kotlin/` in Android Studio and run on emulator/device.

Implemented screens:

- Home screen with vibe text + per-platform actions
- Playlist preview list


## Create a downloadable project archive

From the repository root, run:

```bash
./scripts/create-archive.sh
```

This creates `artifacts/vibez-code.zip` (excluding `.git`, build outputs, and dependency folders).

## Security notes

- OAuth access tokens are persisted in `connected_accounts`
- Use encrypted storage/KMS in production
- Add token refresh and rotation jobs before production rollout

## Production hardening recommendations

- Add end-to-end OAuth callback validation with signed state JWT
- Add retry/backoff and rate-limit handling for each platform API
- Add platform-specific audio-features enrichment for better ranking
- Add monitoring, structured logging, and alerts
