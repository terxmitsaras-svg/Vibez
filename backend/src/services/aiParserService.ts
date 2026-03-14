import OpenAI from 'openai';
import { z } from 'zod';
import { env } from '../config/env.js';
import { VibeAttributes } from '../models/types.js';

const vibeSchema = z.object({
  genre: z.string(),
  mood: z.string(),
  energy: z.number().min(0).max(1),
  danceability: z.number().min(0).max(1),
  tempoRange: z.tuple([z.number().min(40).max(220), z.number().min(40).max(220)]),
  instrumentFocus: z.array(z.string()),
  keywords: z.array(z.string())
});

const client = new OpenAI({ apiKey: env.openAiKey });

export async function parseVibe(vibeText: string): Promise<VibeAttributes> {
  if (!env.openAiKey) {
    return fallbackVibe(vibeText);
  }

  const response = await client.chat.completions.create({
    model: env.openAiModel,
    temperature: 0.2,
    response_format: { type: 'json_object' },
    messages: [
      {
        role: 'system',
        content:
          'You convert a music vibe into structured JSON with keys genre,mood,energy,danceability,tempoRange,instrumentFocus,keywords.'
      },
      { role: 'user', content: vibeText }
    ]
  });

  const raw = response.choices[0]?.message?.content ?? '{}';
  const parsed = vibeSchema.safeParse(JSON.parse(raw));

  if (!parsed.success) {
    return fallbackVibe(vibeText);
  }

  return parsed.data;
}

function fallbackVibe(vibeText: string): VibeAttributes {
  const normalized = vibeText.toLowerCase();
  return {
    genre: normalized.includes('jazz') ? 'jazz' : normalized.includes('trap') ? 'trap' : 'pop',
    mood: normalized.includes('sad') ? 'melancholic' : normalized.includes('dark') ? 'dark' : 'chill',
    energy: normalized.includes('gym') ? 0.85 : 0.55,
    danceability: normalized.includes('dance') ? 0.8 : 0.5,
    tempoRange: normalized.includes('lofi') ? [70, 95] : [100, 140],
    instrumentFocus: normalized.includes('piano') ? ['piano'] : ['drums', 'bass'],
    keywords: vibeText.split(' ').filter(Boolean)
  };
}
