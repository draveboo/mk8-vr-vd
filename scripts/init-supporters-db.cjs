const fs = require('fs');
const path = require('path');
const { neon } = require('@neondatabase/serverless');

const envPath = path.join(__dirname, '..', '.env.local');
if (!process.env.DATABASE_URL && fs.existsSync(envPath)) {
  for (const line of fs.readFileSync(envPath, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^DATABASE_URL=(.*)$/);
    if (match) process.env.DATABASE_URL = match[1].replace(/^"|"$/g, '');
  }
}

if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required. Run `vercel env pull .env.local --yes` first.');

const sql = neon(process.env.DATABASE_URL);

async function main() {
  await sql`
    CREATE TABLE IF NOT EXISTS supporter_claims (
      token TEXT PRIMARY KEY,
      square_order_id TEXT UNIQUE,
      amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
      payment_id TEXT UNIQUE,
      status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'completed')),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      completed_at TIMESTAMPTZ
    )`;
  await sql`
    CREATE TABLE IF NOT EXISTS supporters (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      claim_token TEXT NOT NULL UNIQUE REFERENCES supporter_claims(token) ON DELETE CASCADE,
      handle TEXT NOT NULL UNIQUE,
      display_name TEXT NOT NULL,
      tier TEXT NOT NULL,
      amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
      is_public BOOLEAN NOT NULL DEFAULT TRUE,
      discord_user_id TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )`;
  await sql`
    CREATE TABLE IF NOT EXISTS square_webhook_events (
      event_id TEXT PRIMARY KEY,
      received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )`;
  await sql`ALTER TABLE supporters ADD COLUMN IF NOT EXISTS contact_email TEXT`;
  console.log('VKXR supporter tables are ready.');
}

main().catch((error) => { console.error(error.message); process.exit(1); });
