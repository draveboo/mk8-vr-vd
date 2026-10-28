const { neon } = require('@neondatabase/serverless');

let sql;

function getDb() {
  if (!sql) {
    const url = process.env.DATABASE_URL || process.env.POSTGRES_URL || process.env.POSTGRES_PRISMA_URL || process.env.DATABASE_URL_UNPOOLED;
    if (!url) throw new Error('DATABASE_URL is not configured.');
    sql = neon(url);
  }
  return sql;
}

module.exports = { getDb };
