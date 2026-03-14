import { Router } from 'express';
import { z } from 'zod';
import { env } from '../config/env.js';
import { Platform } from '../models/types.js';
import { upsertConnectedAccount } from '../services/accountService.js';
import { buildAuthUrl, exchangeCode } from '../services/oauthService.js';

const router = Router();
const platformSchema = z.enum(['spotify', 'youtube', 'soundcloud', 'deezer']);

router.get('/:platform/url', (req: any, res: any) => {
  const userId = String(req.query.userId ?? '');
  const parsed = platformSchema.safeParse(req.params.platform);
  if (!parsed.success || !userId) return res.status(400).json({ error: 'Invalid platform or missing userId' });

  const authUrl = buildAuthUrl(parsed.data, userId);
  res.json({ authUrl });
});

router.post('/:platform/callback', async (req: any, res: any) => {
  const parsed = platformSchema.safeParse(req.params.platform);
  const body = z.object({ code: z.string(), userId: z.string() }).safeParse(req.body);

  if (!parsed.success || !body.success) return res.status(400).json({ error: 'Invalid payload' });

  const platform = parsed.data as Platform;
  const token = await exchangeCode(platform, body.data.code);

  await upsertConnectedAccount({
    userId: body.data.userId,
    platform,
    accessToken: token.accessToken,
    refreshToken: token.refreshToken,
    expiresAt: token.expiresIn ? new Date(Date.now() + token.expiresIn * 1000) : undefined
  });

  res.json({ connected: true, redirectTo: env.frontendCallbackUrl });
});

export default router;
