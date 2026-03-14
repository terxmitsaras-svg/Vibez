import { Router } from 'express';
import { z } from 'zod';
import { generatePlaylist, generatePlaylistVariants } from '../services/playlistService.js';

const router = Router();

const requestSchema = z.object({
  userId: z.string(),
  platform: z.enum(['spotify', 'youtube', 'soundcloud', 'deezer']),
  vibeText: z.string().min(3),
  targetTrackCount: z.number().int().min(20).max(40).optional()
});

router.post('/generate', async (req: any, res: any, next: any) => {
  try {
    const parsed = requestSchema.parse(req.body);
    const playlist = await generatePlaylist(parsed);
    res.json(playlist);
  } catch (error) {
    next(error);
  }
});

router.post('/generate-variants', async (req: any, res: any, next: any) => {
  try {
    const parsed = requestSchema.parse(req.body);
    const variants = await generatePlaylistVariants(parsed);
    res.json({ variants });
  } catch (error) {
    next(error);
  }
});

export default router;
