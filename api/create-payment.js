const crypto = require('crypto');

const ENDPOINTS = {
  sandbox: 'https://connect.squareupsandbox.com/v2/payments',
  production: 'https://connect.squareup.com/v2/payments',
};

module.exports = async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });
  const { sourceId, amount } = req.body || {};
  const cents = Number(amount);
  if (!sourceId || !Number.isInteger(cents) || cents < 100 || cents > 1000000) {
    return res.status(400).json({ error: 'Enter a donation between $1 and $10,000.' });
  }
  const token = process.env.SQUARE_ACCESS_TOKEN;
  const locationId = process.env.SQUARE_LOCATION_ID;
  const environment = process.env.SQUARE_ENVIRONMENT === 'sandbox' ? 'sandbox' : 'production';
  if (!token || !locationId) return res.status(500).json({ error: 'Payment service is not configured.' });
  const response = await fetch(ENDPOINTS[environment], {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Square-Version': '2026-08-19' },
    body: JSON.stringify({
      idempotency_key: crypto.randomUUID(),
      source_id: sourceId,
      location_id: locationId,
      amount_money: { amount: cents, currency: 'USD' },
      note: 'VKXR donation',
    }),
  });
  const payload = await response.json();
  if (!response.ok) return res.status(response.status).json({ error: payload.errors?.[0]?.detail || 'Payment declined.' });
  return res.status(200).json({ paymentId: payload.payment?.id, status: payload.payment?.status });
};
