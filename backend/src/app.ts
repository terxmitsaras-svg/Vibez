import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import oauthRoutes from './routes/oauthRoutes.js';
import playlistRoutes from './routes/playlistRoutes.js';
import { errorHandler } from './middleware/errorHandler.js';

export const app = express();

app.use(helmet());
app.use(cors());
app.use(express.json());

app.get('/health', (_req: any, res: any) => {
  res.json({ ok: true });
});

app.use('/oauth', oauthRoutes);
app.use('/playlists', playlistRoutes);
app.use(errorHandler);
