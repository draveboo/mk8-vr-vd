const crypto = require('crypto');
const { getDb } = require('../lib/db');

const ACCESS_COOKIE = 'vkxr_video_access';
const COOKIE_MAX_AGE = 60 * 60 * 24 * 7;
const VALID_DIRECTIONS = new Set(['comfort', 'native', 'more-games', 'polish']);

function hash(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

function cookieValue(header, name) {
  const entry = String(header || '').split(';').map((part) => part.trim()).find((part) => part.startsWith(name + '='));
  return entry ? entry.slice(name.length + 1) : '';
}

module.exports = async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store, private');
  if (req.method !== 'POST' && req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed.' });

  try {
    const sql = getDb();
    if (req.method === 'GET') {
      const token = cookieValue(req.headers.cookie, ACCESS_COOKIE);
      if (!/^[a-f0-9]{64}$/.test(token)) return res.status(200).json({ allowed: false });
      const rows = await sql`
        SELECT id FROM video_access_signups
        WHERE access_token_hash = ${hash(token)} AND access_expires_at > NOW()
        LIMIT 1`;
      return res.status(200).json({ allowed: rows.length > 0 });
    }

    const { email, directionChoice, emailUpdatesOptIn, website } = req.body || {};
    const normalizedEmail = String(email || '').trim().toLowerCase();
    if (website) return res.status(202).json({ ok: true });
    if (!normalizedEmail || normalizedEmail.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
      return res.status(400).json({ error: 'Enter a valid email address.' });
    }
    if (!VALID_DIRECTIONS.has(directionChoice)) return res.status(400).json({ error: 'Choose the direction you would most like to see next.' });

    const token = crypto.randomBytes(32).toString('hex');
    await sql`
      INSERT INTO video_access_signups (email, direction_choice, email_updates_opt_in, access_token_hash, access_expires_at)
      VALUES (${normalizedEmail}, ${directionChoice}, ${emailUpdatesOptIn === true}, ${hash(token)}, NOW() + INTERVAL '7 days')
      ON CONFLICT (email) DO UPDATE SET
        direction_choice = EXCLUDED.direction_choice,
        email_updates_opt_in = EXCLUDED.email_updates_opt_in,
        access_token_hash = EXCLUDED.access_token_hash,
        access_expires_at = EXCLUDED.access_expires_at,
        updated_at = NOW()`;

    res.setHeader('Set-Cookie', `${ACCESS_COOKIE}=${token}; Max-Age=${COOKIE_MAX_AGE}; Path=/; HttpOnly; Secure; SameSite=Lax`);
    return res.status(200).json({ ok: true });
  } catch (error) {
    console.error('Video access request failed:', error.message);
    return res.status(500).json({ error: 'We could not save your access just now. Please try again.' });
  }
};
