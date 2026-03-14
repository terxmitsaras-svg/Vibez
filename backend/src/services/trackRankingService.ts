import { Track, VibeAttributes } from '../models/types.js';

export function rankTracks(tracks: Track[], vibe: VibeAttributes): Track[] {
  return tracks
    .map((track) => ({
      track,
      score:
        scoreGenre(track, vibe) * 0.3 +
        scoreMood(track, vibe) * 0.2 +
        scoreTempo(track, vibe) * 0.2 +
        scoreEnergy(track, vibe) * 0.1 +
        normalizePopularity(track.popularity) * 0.2
    }))
    .sort((a, b) => b.score - a.score)
    .map((entry) => entry.track);
}

function scoreGenre(track: Track, vibe: VibeAttributes): number {
  return track.genres?.some((genre) => genre.toLowerCase().includes(vibe.genre.toLowerCase())) ? 1 : 0.5;
}

function scoreMood(track: Track, vibe: VibeAttributes): number {
  return track.moodTags?.some((m) => m.toLowerCase().includes(vibe.mood.toLowerCase())) ? 1 : 0.4;
}

function scoreTempo(track: Track, vibe: VibeAttributes): number {
  if (!track.tempo) return 0.5;
  return track.tempo >= vibe.tempoRange[0] && track.tempo <= vibe.tempoRange[1] ? 1 : 0.25;
}

function scoreEnergy(track: Track, vibe: VibeAttributes): number {
  if (track.energy === undefined) return 0.5;
  return 1 - Math.min(1, Math.abs(track.energy - vibe.energy));
}

function normalizePopularity(popularity: number): number {
  const cap = Math.max(0, Math.min(100, popularity));
  return cap / 100;
}
