# Backend setup (Supabase)

The backend lives in `supabase/`. Migrations are plain SQL and can be applied
through the Supabase dashboard SQL editor or the management API.

## 1. Project

Create a Supabase project (or use the one configured for this platform). Note:

- Project URL: `https://<ref>.supabase.co`
- Anon key (safe to embed in the apps)

## 2. Apply migrations

Run, in order, the files in `supabase/migrations/`:

1. `0001_init.sql` — tables, RLS policies, triggers, SECURITY DEFINER RPCs
2. `0002_seed_default_plan.sql` — default plan (PKR 5,000 / 30 days) and
   default `app_settings` (`binance_pay_id`, `trc20_address`, `ai_config`)

The signup trigger `handle_new_user` creates the pending `profiles` and
`app_access` rows automatically, including the WhatsApp number from signup
metadata.

## 3. Point the apps at the project

Set the URL and anon key in the app modules' `build.gradle.kts`
(`SUPABASE_URL`, `SUPABASE_ANON_KEY`), or better: pass them at build time from
GitHub Actions secrets to avoid editing files:

```
buildConfigField("String", "SUPABASE_URL", "\"" + System.getenv("SUPABASE_URL") + "\"")
```

## 4. Edge Functions

Deploy each function under `supabase/functions/`:

```
supabase functions deploy ai-gateway
supabase functions deploy create-subscription-payment
supabase functions deploy nowpayments-webhook
supabase functions deploy backend-health
```

Then set secrets (never commit them):

```
SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY        # required by all functions
OPENROUTER_API_KEY / GROQ_API_KEY / ...        # only for providers you use
NOWPAYMENTS_API_KEY, NOWPAYMENTS_IPN_SECRET    # only for crypto checkout
```

With no AI provider secrets set, the gateway fails closed and reports
unavailable — that is correct behavior, not a bug.

## 5. Payments configuration

The administrator sets the Binance Pay ID and TRC20 receiving address in the
Admin app (stored in `app_settings`, never compiled into APKs). Automated crypto
checkout via NOWPayments stays disabled until merchant onboarding is done and
its secrets are set; see docs/SECURITY.md for the fail-closed rules.

## Local/test backend

For development without a cloud project, run Supabase locally (Docker) and point
`SUPABASE_URL` at `http://10.0.2.2:54321` for the Android emulator. Production
uses a separate project; never mix them.
