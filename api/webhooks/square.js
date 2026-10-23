const crypto = require('crypto');
const { getDb } = require('../../lib/db');

exports.config = { api: { bodyParser: false } };

function rawBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

module.exports = async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).end();
  const signatureKey = process.env.SQUARE_WEBHOOK_SIGNATURE_KEY;
  const notificationUrl = process.env.SQUARE_WEBHOOK_NOTIFICATION_URL;
  const signature = req.headers['x-square-hmacsha256-signature'];
  if (!signatureKey || !notificationUrl || !signature) return res.status(503).send('Square webhook is not configured.');

  try {
    const body = await rawBody(req);
    const expected = crypto.createHmac('sha256', signatureKey).update(notificationUrl + body.toString('utf8')).digest('base64');
    const provided = Buffer.from(signature);
    const expectedBuffer = Buffer.from(expected);
    if (provided.length !== expectedBuffer.length || !crypto.timingSafeEqual(provided, expectedBuffer)) return res.status(403).send('Invalid signature.');

    const event = JSON.parse(body.toString('utf8'));
    const eventId = event.event_id;
    if (!eventId) return res.status(400).send('Missing event ID.');
    const sql = getDb();
    const inserted = await sql`INSERT INTO square_webhook_events (event_id) VALUES (${eventId}) ON CONFLICT DO NOTHING RETURNING event_id`;
    if (!inserted[0]) return res.status(200).send('Already processed.');

    const payment = event.data?.object?.payment;
    if (event.type === 'payment.updated' && payment?.status === 'COMPLETED' && payment.order_id) {
      await sql`
        UPDATE supporter_claims SET payment_id = ${payment.id}, status = 'completed', completed_at = COALESCE(completed_at, NOW())
        WHERE square_order_id = ${payment.order_id} AND amount_cents = ${Number(payment.amount_money?.amount || 0)}`;
    }
    return res.status(200).send('OK');
  } catch (error) {
    console.error('Square webhook failed:', error.message);
    return res.status(500).send('Webhook processing failed.');
  }
};
