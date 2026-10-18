# Security

## Credentials

- **Binance API key/secret** are stored only in the app's Android Keystore-backed
  `CredentialStore` (AES-256-GCM, non-exportable key). They are never written to
  the database, logs, GitHub, or any AI provider. Withdrawal permissions must
  never be granted to the key; the app never requests them and contains no
  withdrawal code. Prefer exchange-supported IP restrictions on the key.
- **Supabase service-role key** lives only in Edge Function secrets. The apps
  embed only the anon key plus the user's own session token.
- **AI provider keys** live only in Edge Function secrets
  (`OPENROUTER_API_KEY`, `GROQ_API_KEY`, `GEMINI_API_KEY`, `OPENAI_API_KEY`).
- **Payment secrets** (`NOWPAYMENTS_API_KEY`, `NOWPAYMENTS_IPN_SECRET`) live only
  in Edge Function secrets. The webhook fails closed (503) when the secret is
  absent; a missing secret never activates anything.
- CI cannot sign releases until you add your own keystore as repository secrets;
  until then release APKs are unsigned test builds.

## Server-side enforcement

- All privileged actions run through SECURITY DEFINER RPCs that check
  `admin_users` membership and write to `audit_log`. Admin rights are never
  granted from a client flag or a public email string.
- Row Level Security is enabled on every table. Users see only their own rows;
  `admin_users`, `app_settings`, `audit_log` and `error_events` have no client
  policies at all.
- Subscription expiry is computed and enforced server-side in `get_my_access`,
  not just in the UI.
- Devices: a client-supplied device key is never proof of identity — the server
  validates the session token and matches the registered device key. Re-login on
  the same approved device keeps approved status; a new device requires admin
  approval or the documented transfer process.
- Payments: manual payments stay `pending` until an admin verifies them.
  The crypto webhook verifies HMAC-SHA512 signatures, checks the expected amount,
  deduplicates provider transaction references, and activates the subscription
  atomically.

## What this code deliberately does not do

- No withdrawal endpoints, no live order execution in v1.
- No fabricated AI output, confidence scores, or market data.
- No auto-lowering of scanner thresholds to force signals.
- No claim that the emergency stop closes positions — it only blocks new
  entries.
- No secrets in this repository. If you find one, rotate it immediately.

## Reporting

Open a GitHub issue or contact the repository owner. Do not post secrets in
issues.
