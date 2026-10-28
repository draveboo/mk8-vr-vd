const { neon } = require('@neondatabase/serverless');

let sql;

function getDb() {
  if (!sql) {
    const candidates = [
      process.env.POSTGRES_URL,
      process.env.DATABASE_URL,
      process.env.DATABASE_URL_UNPOOLED,
      process.env.POSTGRES_PRISMA_URL,
    ];
    const url = candidates.find(c => typeof c === 'string' && (c.startsWith('postgres://') || c.startsWith('postgresql://')));
    if (!url) {
      throw new Error('Valid PostgreSQL connection URL starting with postgres:// or postgresql:// was not found.');
    }
    sql = neon(url);
  }
  return sql;
}

module.exports = { getDb };
