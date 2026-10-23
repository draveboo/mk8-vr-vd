const { getDb } = require('../../lib/db');

function safeEqual(left, right) {
  const a = Buffer.from(left);
  const b = Buffer.from(right);
  return a.length === b.length && require('crypto').timingSafeEqual(a, b);
}

function authorized(req, expectedPassword) {
  const header = String(req.headers.authorization || '');
  const match = header.match(/^Basic\s+([A-Za-z0-9+/=]+)$/i);
  if (!match) return false;
  let decoded;
  try { decoded = Buffer.from(match[1], 'base64').toString('utf8'); } catch { return false; }
  const colon = decoded.indexOf(':');
  if (colon < 0 || decoded.slice(0, colon) !== 'vkxr-owner') return false;
  return safeEqual(decoded.slice(colon + 1), expectedPassword);
}

module.exports = async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store, private');
  res.setHeader('X-Robots-Tag', 'noindex, nofollow');
  if (req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed.' });
  const password = process.env.SUPPORT_ADMIN_PASSWORD;
  if (!password) return res.status(503).json({ error: 'The private priority inbox needs its Vercel password setting first.' });
  if (!authorized(req, password)) return res.status(401).json({ error: 'Password not recognized.' });

  try {
    const sql = getDb();
    const rows = await sql`
      SELECT c.completed_at, c.amount_cents, c.payment_id,
             s.display_name, s.handle, s.tier, s.contact_email,
             s.email_updates_opt_in, s.priority_request, s.discord_user_id, s.is_public
      FROM supporter_claims c
      LEFT JOIN supporters s ON s.claim_token = c.token
      WHERE c.status = 'completed' AND c.payment_id IS NOT NULL
      ORDER BY c.amount_cents DESC, c.completed_at ASC
      LIMIT 500`;
    return res.status(200).json({ supporters: rows });
  } catch (error) {
    console.error('Priority inbox failed:', error.message);
    return res.status(500).json({ error: 'Could not load verified supporter records.' });
  }
};
