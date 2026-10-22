const SQUARE_ENDPOINT = 'https://connect.squareup.com/v2/online-checkout/payment-links';

module.exports = async function handler(req, res) {
  const amount = Number(req.query?.amount || 5);
  const cents = Math.round(amount * 100);
  if (![500, 1000, 2500, 5000].includes(cents)) return res.status(400).send('Choose a supported donation amount.');

  const token = process.env.SQUARE_ACCESS_TOKEN;
  const locationId = process.env.SQUARE_LOCATION_ID;
  if (!token || !locationId) return res.status(500).send('Donation portal is not configured.');

  const response = await fetch(SQUARE_ENDPOINT, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Square-Version': '2026-08-19' },
    body: JSON.stringify({
      idempotency_key: crypto.randomUUID(),
      quick_pay: {
        name: `Support VKXR ($${(cents / 100).toFixed(2)})`,
        price_money: { amount: cents, currency: 'USD' },
        location_id: locationId,
      },
    }),
  });
  const payload = await response.json();
  if (!response.ok || !payload.payment_link?.url) return res.status(502).send('Square could not create the secure donation portal.');
  return res.redirect(302, payload.payment_link.url);
};
