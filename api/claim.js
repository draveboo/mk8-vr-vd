const { getDb } = require('../lib/db');

const SQUARE_API = 'https://connect.squareup.com/v2/payments/';
const VALID_HANDLE = /^[a-z0-9][a-z0-9-]{2,23}$/;

function tierFor(amount) {
  if (amount >= 5000) return 'Rainbow Road Patron';
  if (amount >= 2500) return 'Star Cup Supporter';
  if (amount >= 1000) return 'Mushroom Cup Supporter';
  return 'Pit Crew Supporter';
}

module.exports = async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed.' });
  const { claim, transactionId, orderId, displayName, handle, contactEmail, priorityRequest, isPublic = true } = req.body || {};
  const normalizedHandle = String(handle || '').trim().toLowerCase();
  const normalizedName = String(displayName || '').trim().replace(/\s+/g, ' ');
  const normalizedEmail = String(contactEmail || '').trim().toLowerCase();
  const normalizedRequest = String(priorityRequest || '').trim().replace(/\s+/g, ' ');

  if (!/^[a-f0-9-]{24,80}$/i.test(String(claim || ''))) return res.status(400).json({ error: 'That supporter link is invalid.' });
  if (!VALID_HANDLE.test(normalizedHandle)) return res.status(400).json({ error: 'Use 3–24 lowercase letters, numbers, or hyphens for your callsign.' });
  if (!normalizedName || normalizedName.length > 48) return res.status(400).json({ error: 'Enter a display name up to 48 characters.' });
  if (normalizedEmail && (normalizedEmail.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail))) return res.status(400).json({ error: 'Enter a valid email address or leave it blank.' });
  if (normalizedRequest.length > 600) return res.status(400).json({ error: 'Priority request must be 600 characters or less.' });
  if (!/^[A-Za-z0-9_-]{8,128}$/.test(String(transactionId || ''))) return res.status(400).json({ error: 'Square did not send a valid payment reference.' });

  try {
    const sql = getDb();
    const claims = await sql`SELECT token, square_order_id, amount_cents, status FROM supporter_claims WHERE token = ${claim}`;
    const supporterClaim = claims[0];
    if (!supporterClaim) return res.status(404).json({ error: 'This supporter link was not found.' });
    if (orderId !== supporterClaim.square_order_id) return res.status(403).json({ error: 'That Square order does not match this supporter link.' });

    const token = process.env.SQUARE_ACCESS_TOKEN;
    if (!token) throw new Error('Square is not configured.');
    const squareResponse = await fetch(`${SQUARE_API}${encodeURIComponent(transactionId)}`, {
      headers: { Authorization: `Bearer ${token}`, 'Square-Version': '2026-08-19' },
    });
    const squarePayload = await squareResponse.json();
    const payment = squarePayload.payment;
    if (!squareResponse.ok || !payment || payment.status !== 'COMPLETED') return res.status(409).json({ error: 'Square has not confirmed this payment yet. Please try again in a moment.' });
    if (payment.order_id !== supporterClaim.square_order_id || Number(payment.amount_money?.amount) !== supporterClaim.amount_cents || payment.amount_money?.currency !== 'USD') {
      return res.status(403).json({ error: 'That payment does not match this supporter link.' });
    }

    await sql`UPDATE supporter_claims SET payment_id = ${payment.id}, status = 'completed', completed_at = COALESCE(completed_at, NOW()) WHERE token = ${claim}`;
    const tier = tierFor(supporterClaim.amount_cents);
    const supporters = await sql`
      INSERT INTO supporters (claim_token, handle, display_name, contact_email, priority_request, tier, amount_cents, is_public)
      VALUES (${claim}, ${normalizedHandle}, ${normalizedName}, ${normalizedEmail || null}, ${normalizedRequest || null}, ${tier}, ${supporterClaim.amount_cents}, ${Boolean(isPublic)})
      ON CONFLICT (claim_token) DO UPDATE SET
        handle = EXCLUDED.handle,
        display_name = EXCLUDED.display_name,
        contact_email = COALESCE(EXCLUDED.contact_email, supporters.contact_email),
        priority_request = COALESCE(EXCLUDED.priority_request, supporters.priority_request),
        is_public = EXCLUDED.is_public,
        updated_at = NOW()
      RETURNING handle, display_name, tier, is_public`;
    const profileQuery = new URLSearchParams({ handle: normalizedHandle });
    if (!Boolean(isPublic)) profileQuery.set('token', claim);
    return res.status(200).json({ supporter: supporters[0], profileUrl: `/pit-crew.html?${profileQuery}` });
  } catch (error) {
    if (error.code === '23505') return res.status(409).json({ error: 'That callsign is already in the Pit Crew. Try another one.' });
    console.error('Supporter claim failed:', error.message);
    return res.status(500).json({ error: 'We could not create your supporter card yet.' });
  }
};
