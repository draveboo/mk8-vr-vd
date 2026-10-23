const crypto = require('crypto');
const { getDb } = require('../../lib/db');

const CALLBACK_URL = 'https://vkxr.vercel.app/api/discord/callback';
const CLAIM_PATTERN = /^[a-f0-9-]{24,80}$/i;

module.exports = async function handler(req, res) {
  if (req.method !== 'GET') return res.status(405).send('Method not allowed.');
  const { DISCORD_CLIENT_ID: clientId, DISCORD_CLIENT_SECRET: clientSecret } = process.env;
  const claim = String(req.query?.claim || '');
  if (!clientId || !clientSecret || !process.env.DISCORD_GUILD_ID || !process.env.DISCORD_BOT_TOKEN || !process.env.DISCORD_SUPPORTER_ROLE_ID) {
    return res.status(503).send('Discord role linking is not configured yet. Your supporter card and payment are saved.');
  }
  if (!CLAIM_PATTERN.test(claim)) return res.status(400).send('That supporter link is invalid.');

  try {
    const sql = getDb();
    const rows = await sql`SELECT claim_token, handle, is_public, discord_user_id FROM supporters WHERE claim_token = ${claim} LIMIT 1`;
    if (!rows[0]) return res.status(404).send('Create your verified Pit Crew card before connecting Discord.');
    const nonce = crypto.randomBytes(24).toString('hex');
    const serialized = Buffer.from(JSON.stringify({ nonce, claim, expires: Date.now() + 10 * 60 * 1000 })).toString('base64url');
    const signature = crypto.createHmac('sha256', clientSecret).update(serialized).digest('base64url');
    res.setHeader('Set-Cookie', `discord_oauth_state=${serialized}.${signature}; HttpOnly; Secure; SameSite=Lax; Path=/api/discord; Max-Age=600`);

    const authorize = new URL('https://discord.com/oauth2/authorize');
    authorize.searchParams.set('client_id', clientId);
    authorize.searchParams.set('response_type', 'code');
    authorize.searchParams.set('redirect_uri', CALLBACK_URL);
    authorize.searchParams.set('scope', 'identify guilds.join');
    authorize.searchParams.set('state', nonce);
    authorize.searchParams.set('prompt', 'consent');
    return res.redirect(302, authorize.toString());
  } catch (error) {
    console.error('Discord OAuth start failed:', error.message);
    return res.status(500).send('Could not start Discord linking.');
  }
};
