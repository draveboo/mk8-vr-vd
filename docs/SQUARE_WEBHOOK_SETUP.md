# Square payment webhook

The site verifies a payment directly with Square before issuing a Pit Crew card. The webhook is an additional signed record of completed support payments.

In Square Developer Dashboard, create a production webhook subscription:

- Event: `payment.updated`
- Notification URL: `https://vkxr.vercel.app/api/webhooks/square`

Copy the webhook signature key into Vercel as the production secret `SQUARE_WEBHOOK_SIGNATURE_KEY`, then add this production config value:

`SQUARE_WEBHOOK_NOTIFICATION_URL=https://vkxr.vercel.app/api/webhooks/square`

The notification URL must exactly match the Square subscription URL for signature verification to work. Do not add the signature key to Git or the client-side site.
