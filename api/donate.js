const crypto = require('crypto');
const { getDb } = require('../lib/db');
const SQUARE_ENDPOINT = 'https://connect.squareup.com/v2/online-checkout/payment-links';

module.exports = async function handler(req, res) {
  const amount = Number(req.query?.amount || 5);
  const cents = Math.round(amount * 100);
  if (![500, 1000, 2500, 5000].includes(cents)) return res.status(400).send('Choose a supported donation amount.');

  const token = process.env.SQUARE_ACCESS_TOKEN;
  const locationId = process.env.SQUARE_LOCATION_ID;
  if (!token || !locationId) return res.status(500).send('Donation portal is not configured.');
  const claimToken = crypto.randomUUID();
  const siteUrl = process.env.SITE_URL || `https://${req.headers.host || 'vkxr.vercel.app'}`;

  let response;
  let payload;
  try {
    response = await fetch(SQUARE_ENDPOINT, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Square-Version': '2026-08-19' },
    body: JSON.stringify({
      idempotency_key: crypto.randomUUID(),
      order: {
        location_id: locationId,
        reference_id: claimToken,
        line_items: [{
          name: `Support VKXR ($${(cents / 100).toFixed(2)})`,
          quantity: '1',
          base_price_money: { amount: cents, currency: 'USD' },
        }],
      },
      checkout_options: { redirect_url: `${siteUrl}/support.html?claim=${encodeURIComponent(claimToken)}` },
    }),
    });
    payload = await response.json();
    if (!response.ok || !payload.payment_link?.url || !payload.payment_link?.order_id) throw new Error('Square could not create the secure donation portal.');
    const sql = getDb();
    await sql`INSERT INTO supporter_claims (token, square_order_id, amount_cents) VALUES (${claimToken}, ${payload.payment_link.order_id}, ${cents})`;
    return res.redirect(302, payload.payment_link.url);
  } catch (error) {
    console.error('Square donation link failed:', error.message);
    return res.status(502).send('Square could not create the secure donation portal.');
  }
};
