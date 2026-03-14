import { query } from '../db/client.js';
import { ConnectedAccount, Platform } from '../models/types.js';

export async function upsertConnectedAccount(account: ConnectedAccount): Promise<void> {
  await query(
    `INSERT INTO connected_accounts (user_id, platform, access_token, refresh_token, expires_at)
     VALUES ($1,$2,$3,$4,$5)
     ON CONFLICT (user_id, platform)
     DO UPDATE SET access_token = excluded.access_token,
                   refresh_token = excluded.refresh_token,
                   expires_at = excluded.expires_at,
                   updated_at = NOW()`,
    [account.userId, account.platform, account.accessToken, account.refreshToken ?? null, account.expiresAt ?? null]
  );
}

export async function getConnectedAccount(userId: string, platform: Platform): Promise<ConnectedAccount | null> {
  const rows = await query<ConnectedAccount & { expires_at?: string }>(
    'SELECT user_id as "userId", platform, access_token as "accessToken", refresh_token as "refreshToken", expires_at as "expiresAt" FROM connected_accounts WHERE user_id=$1 AND platform=$2',
    [userId, platform]
  );

  if (!rows.length) return null;

  const account = rows[0];
  return {
    ...account,
    expiresAt: account.expiresAt ? new Date(account.expiresAt) : undefined
  };
}
