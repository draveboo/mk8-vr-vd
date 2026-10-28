const crypto = require('crypto');
const { getDb } = require('../lib/db');

function hashKey(key) {
  return crypto.createHash('sha256').update(String(key)).digest('hex');
}

/**
 * Sovereign Vault API
 * Links User Email + Cryptographic Access Key + Square Payment Reference + Matrix Value State
 */
module.exports = async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store, private');
  let sql;
  try {
    sql = getDb();
  } catch (dbErr) {
    console.error('Database connection failed:', dbErr.message);
    return res.status(503).json({ error: 'Database service unavailable.', detail: dbErr.message });
  }


  // GET: Restore / Auto-insert Matrix Data by Email + Cryptographic Key
  if (req.method === 'GET') {
    const email = String(req.query?.email || '').trim().toLowerCase();
    const key = String(req.query?.key || '').trim();

    if (!email || !key) {
      return res.status(400).json({ error: 'Email and cryptographic key are required to restore your vault.' });
    }

    try {
      const rows = await sql`
        SELECT email, payment_id, matrix_state, created_at, updated_at
        FROM sovereign_vault
        WHERE email = ${email} AND key_hash = ${hashKey(key)}
        LIMIT 1
      `;

      if (!rows.length) {
        return res.status(401).json({ error: 'Invalid cryptographic key or email record not found.' });
      }

      return res.status(200).json({
        ok: true,
        email: rows[0].email,
        paymentId: rows[0].payment_id,
        matrixState: rows[0].matrix_state,
        updatedAt: rows[0].updated_at
      });
    } catch (error) {
      console.error('Vault retrieval failed:', error.message);
      return res.status(500).json({ error: 'Failed to access vault.', detail: error.message });
    }
  }

  // POST: Backup Matrix State / Claim Vault / Link Square Payment
  if (req.method === 'POST') {
    const { email, key, matrixState = {}, paymentId = null } = req.body || {};
    const normalizedEmail = String(email || '').trim().toLowerCase();

    if (!normalizedEmail || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
      return res.status(400).json({ error: 'A valid email address is required.' });
    }

    // Use existing key or generate a fresh 32-byte sovereign cryptographic token
    const clientKey = String(key || '').trim() || crypto.randomBytes(32).toString('hex');
    const hashed = hashKey(clientKey);

    try {
      // Upsert: Save matrix values tied to email and key hash
      const matrixJson = typeof matrixState === 'string' ? matrixState : JSON.stringify(matrixState);
      const rows = await sql`
        INSERT INTO sovereign_vault (email, key_hash, payment_id, matrix_state, updated_at)
        VALUES (${normalizedEmail}, ${hashed}, ${paymentId}, ${matrixJson}::jsonb, NOW())
        ON CONFLICT (email) DO UPDATE SET
          matrix_state = EXCLUDED.matrix_state,
          payment_id = COALESCE(EXCLUDED.payment_id, sovereign_vault.payment_id),
          updated_at = NOW()
        WHERE sovereign_vault.key_hash = ${hashed} OR sovereign_vault.key_hash = EXCLUDED.key_hash
        RETURNING email, payment_id, matrix_state, updated_at
      `;
        WHERE sovereign_vault.key_hash = ${hashed} OR sovereign_vault.key_hash = EXCLUDED.key_hash
        RETURNING email, payment_id, matrix_state, updated_at
      `;

      if (!rows.length) {
        return res.status(403).json({ error: 'Cryptographic key mismatch for this email vault.' });
      }

      return res.status(200).json({
        ok: true,
        message: 'Your sovereign matrix values are backed up.',
        email: rows[0].email,
        key: clientKey, // Returned to user to save locally / keep in email
        matrixState: rows[0].matrix_state,
        updatedAt: rows[0].updated_at
      });
    } catch (error) {
      console.error('Vault save failed:', error.message);
      return res.status(500).json({ error: 'Failed to preserve matrix state in vault.', detail: error.message });
    }
  }

  return res.status(405).json({ error: 'Method not allowed.' });
};
