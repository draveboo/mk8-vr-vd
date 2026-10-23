const crypto = require('crypto');
const { getDb } = require('../../lib/db');

const API = 'https://discord.com/api/v10';
const CALLBACK_URL = 'https://vkxr.vercel.app/api/discord/callback';

function readStateCookie(req) {
  const cookies = String(req.headers.cookie || '').split(';');
  const entry = cookies.map((part) => part.trim()).find((part) => part.startsWith('discord_oauth_state='));
  return entry ? decodeURIComponent(entry.slice('discord_oauth_state='.length)) : '';
}

function validState(req, returnedState, secret) {
  const raw = readStateCookie(req);
  const split = raw.lastIndexOf('.');
  if (split < 1) return null;
  const serialized = raw.slice(0, split);
  const signature = raw.slice(split + 1);
  const expected = crypto.createHmac('sha256', secret).update(serialized).digest('base64url');
  const actualBuffer = Buffer.from(signature);
  const expectedBuffer = Buffer.from(expected);
  if (actualBuffer.length !== expectedBuffer.length || !crypto.timingSafeEqual(actualBuffer, expectedBuffer)) return null;
  try {
    const value = JSON.parse(Buffer.from(serialized, 'base64url').toString('utf8'));
    if (value.nonce !== returnedState || value.expires < Date.now()) return null;
    return value.claim;
  } catch { return null; }
}

async function redirectForClaim(res, sql, claim, result) {
  const rows = await sql`SELECT claim_token, handle, is_public FROM supporters WHERE claim_token = ${claim} LIMIT 1`;
  const supporter = rows[0];
  if (!supporter) return res.redirect(303, 'https://vkxr.vercel.app/pit-crew.html?discord=error');
  const url = new URL('https://vkxr.vercel.app/pit-crew.html');
  url.searchParams.set('handle', supporter.handle);
  if (!supporter.is_public) url.searchParams.set('token', supporter.claim_token);
  url.searchParams.set('discord', result);
  return res.redirect(303, url.toString());
}

module.exports = async function handler(req, res) {
  if (req.method !== 'GET') return res.status(405).send('Method not allowed.');
  const { DISCORD_CLIENT_ID: clientId, DISCORD_CLIENT_SECRET: clientSecret, DISCORD_BOT_TOKEN: botToken, DISCORD_GUILD_ID: guildId, DISCORD_SUPPORTER_ROLE_ID: roleId } = process.env;
  if (!clientId || !clientSecret || !botToken || !guildId || !roleId) return res.status(503).send('Discord role linking is not configured.');
  const sql = getDb();
  const claim = validState(req, req.query?.state, clientSecret);
  res.setHeader('Set-Cookie', 'discord_oauth_state=; HttpOnly; Secure; SameSite=Lax; Path=/api/discord; Max-Age=0');
  if (!claim) return res.status(400).send('Discord authorization expired or could not be verified. Return to your supporter page and try again.');
  if (req.query?.error) return redirectForClaim(res, sql, claim, 'error');
  const code = String(req.query?.code || '');
  if (!code) return redirectForClaim(res, sql, claim, 'error');

  try {
    const tokenResponse = await fetch(`${API}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ client_id: clientId, client_secret: clientSecret, grant_type: 'authorization_code', code, redirect_uri: CALLBACK_URL }),
    });
    const tokenPayload = await tokenResponse.json();
    if (!tokenResponse.ok || !tokenPayload.access_token) return redirectForClaim(res, sql, claim, 'error');

    const userResponse = await fetch(`${API}/users/@me`, { headers: { Authorization: `Bearer ${tokenPayload.access_token}` } });
    const userPayload = await userResponse.json();
    if (!userResponse.ok || !/^\d{17,20}$/.test(String(userPayload.id || ''))) return redirectForClaim(res, sql, claim, 'error');

    const supporters = await sql`SELECT claim_token, handle, is_public, discord_user_id FROM supporters WHERE claim_token = ${claim} LIMIT 1`;
    const supporter = supporters[0];
    if (!supporter) return res.status(404).send('The supporter card could not be found.');
    const linked = await sql`SELECT claim_token FROM supporters WHERE discord_user_id = ${userPayload.id} AND claim_token <> ${claim} LIMIT 1`;
    if (linked[0]) return redirectForClaim(res, sql, claim, 'already_linked');
    if (supporter.discord_user_id && supporter.discord_user_id !== userPayload.id) return redirectForClaim(res, sql, claim, 'already_linked');

    const joinResponse = await fetch(`${API}/guilds/${encodeURIComponent(guildId)}/members/${encodeURIComponent(userPayload.id)}`, {
      method: 'PUT',
      headers: { Authorization: `Bot ${botToken}`, 'Content-Type': 'application/json', 'X-Audit-Log-Reason': 'Supporter opted into VKXR Discord linking' },
      body: JSON.stringify({ access_token: tokenPayload.access_token, roles: [roleId] }),
    });
    if (!joinResponse.ok && joinResponse.status !== 204) {
      console.error('Discord guild join returned status:', joinResponse.status);
      return redirectForClaim(res, sql, claim, 'role_setup');
    }
    if (joinResponse.status === 204) {
      const roleResponse = await fetch(`${API}/guilds/${encodeURIComponent(guildId)}/members/${encodeURIComponent(userPayload.id)}/roles/${encodeURIComponent(roleId)}`, {
        method: 'PUT',
        headers: { Authorization: `Bot ${botToken}`, 'X-Audit-Log-Reason': 'Verified VKXR Pit Crew supporter' },
      });
      if (!roleResponse.ok) {
        console.error('Discord role assignment returned status:', roleResponse.status);
        return redirectForClaim(res, sql, claim, roleResponse.status === 404 ? 'join_server' : 'role_setup');
      }
    }

    await sql`UPDATE supporters SET discord_user_id = ${userPayload.id}, updated_at = NOW() WHERE claim_token = ${claim}`;
    return redirectForClaim(res, sql, claim, 'connected');
  } catch (error) {
    console.error('Discord OAuth callback failed:', error.message);
    return redirectForClaim(res, sql, claim, 'error');
  }
};
