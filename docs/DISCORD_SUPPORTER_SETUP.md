# VKXR Pit Crew Discord linking

The app now has a Discord OAuth callback. After a Square-verified donation and Pit Crew card claim, the supporter can optionally connect Discord. If they approve the `guilds.join` scope, the app adds their Discord account to the configured server and grants the `VKXR Pit Crew` role. Supporters who do not use Discord still receive their shareable Pit Crew card and downloadable social image.

## Discord setup

1. In the Discord Developer Portal, create an application and add a bot.
2. Under OAuth2, add this exact redirect URL: `https://vkxr.vercel.app/api/discord/callback`.
3. In the server, create a role named `VKXR Pit Crew`. Give it only the permissions the community needs.
4. Invite the bot to the server with `Create Instant Invite` and `Manage Roles`. Move the bot's highest role above `VKXR Pit Crew` in Server Settings > Roles. Discord requires the bot to be in the server; it needs `CREATE_INSTANT_INVITE` to add consenting users and `MANAGE_ROLES` to give the supporter role.
5. Copy the role ID (enable Developer Mode in Discord, then right-click the role > Copy Role ID).
6. Add the remaining variables to the Vercel project's **Production** environment:
   - `DISCORD_CLIENT_ID` — Config
   - `DISCORD_CLIENT_SECRET` — Secret
   - `DISCORD_BOT_TOKEN` — Secret
   - `DISCORD_SUPPORTER_ROLE_ID` — Config

`DISCORD_GUILD_ID` is already set in Vercel Production to the server ID supplied by the owner. Never commit or paste the client secret or bot token into source code or chat.

After the variables are saved, redeploy Production. The Discord button appears after a supporter claims a verified Pit Crew card. The callback validates OAuth state, links one Discord user to one supporter record, and adds the server role. Donors who are not members yet must join the server first, then retry the link.

## OAuth details

- Scopes requested from the supporter: `identify` and `guilds.join`. Discord asks the user to approve joining the VKXR server.
- Callback: `/api/discord/callback`.
- Bot permissions: `Create Instant Invite` and `Manage Roles`; no message-reading or administrator permissions are requested.
- Discord user tokens are used for the callback only and are not stored. The database stores the Discord user ID on the verified supporter record.
