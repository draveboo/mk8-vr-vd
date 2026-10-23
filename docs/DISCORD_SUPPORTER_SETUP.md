# Discord supporter roles (next step)

The Pit Crew card system is ready for a Discord connection, but it deliberately does not create or guess Discord credentials.

1. Create a Discord application and bot at the Discord Developer Portal.
2. Add the bot to the VKXR server with the `Manage Roles` permission. Place the bot's role above the new `VKXR Pit Crew` role.
3. Add these **production** Vercel environment variables (never commit or paste the secret values):
   - `DISCORD_CLIENT_ID`
   - `DISCORD_CLIENT_SECRET`
   - `DISCORD_BOT_TOKEN`
   - `DISCORD_GUILD_ID`
   - `DISCORD_SUPPORTER_ROLE_ID`
4. Set the OAuth redirect URL to `https://vkxr.vercel.app/api/discord/callback`.

Once those values exist, the next implementation adds a “Connect Discord” button to a verified Pit Crew card, uses Discord OAuth to identify the supporter, then grants only the named server role. No Discord login is required to make a card public or private.

