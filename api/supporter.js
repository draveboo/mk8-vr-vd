const { getDb } = require('../lib/db');

module.exports = async function handler(req, res) {
  if (req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed.' });
  const handle = String(req.query?.handle || '').trim().toLowerCase();
  const token = String(req.query?.token || '');
  if (!/^[a-z0-9][a-z0-9-]{2,23}$/.test(handle)) return res.status(400).json({ error: 'Invalid callsign.' });
  try {
    const sql = getDb();
    const profiles = await sql`
      SELECT handle, display_name, tier, created_at
      FROM supporters
      WHERE handle = ${handle} AND (is_public = TRUE OR claim_token = ${token}) LIMIT 1`;
    if (!profiles[0]) return res.status(404).json({ error: 'This Pit Crew card is private or does not exist.' });
    return res.status(200).json({ supporter: profiles[0] });
  } catch (error) {
    console.error('Supporter profile failed:', error.message);
    return res.status(500).json({ error: 'Could not load this supporter card.' });
  }
};
