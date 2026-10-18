# Trading Master Platform

Production-oriented Android trading platform for Binance USDⓈ-M Futures with three independent apps, a Supabase backend, and GitHub Actions CI/CD. This is a fresh, independently written codebase (see [SECURITY](docs/SECURITY.md) and the disclaimer below).

| Module | App | Package |
|---|---|---|
| `:app` | Trading Master — user scanning & paper trading | `com.tradingmaster.app` |
| `:admin` | Trading Master Admin — administrator console | `com.tradingmaster.admin` |
| `:pro` | Trading Master Pro — premium analysis & AI assistance | `com.tradingmaster.pro` |
| `:core` | Shared engines (scanner, risk, paper, backtest, Binance, Supabase) | `com.tradingmaster.core` |
| `supabase/` | Migrations (PostgreSQL + RLS) and Edge Functions (Deno) | — |

## Build the APKs on GitHub (no computer needed)

1. Open the **Actions** tab of this repository.
2. Select **Android CI** (pushes to `main` also trigger it) and wait for it to finish green.
3. At the bottom of the finished run, download the artifacts: `trading-master-debug-apk`, `trading-master-admin-debug-apk`, `trading-master-pro-debug-apk`.
4. Extract the ZIP on your Android phone, tap the APK, and allow installation from that source.

These are debug/testing builds. Tagged versions (`git tag v0.1.0`) automatically build a GitHub Release with renamed APKs — see [docs/RELEASE.md](docs/RELEASE.md) for signed production builds.

## What is implemented

- **Scanner** (deterministic, unit-tested): up to 100 eligible USDT perpetuals per run, $5M 24h-turnover filter, closed 5m/15m/1h candles, EMA alignment, RSI momentum, relative volume (≥1.35x), ATR stops, ADX trend strength, swing structure, entry-extension chase prevention, R:R-verified TP1/TP2/TP3, and a first-class **NO TRADE** result with reasons. Thresholds never auto-lower to produce signals.
- **Signals** carry entry zone, stop, three targets with risk/reward, a 0–5 ranking score with full explanation, supporting reasons, invalidation conditions, candle timestamps and mandatory risk warnings. A 5/5 score is a ranking, never a probability of success.
- **Paper trading**: fully simulated journal with taker fees, adverse slippage, stop-first handling on ambiguous candles, partial exits. It never sends exchange orders. Funding is not modeled and that is disclosed.
- **Backtesting**: chronological, no lookahead — signals from completed candles, entry on the next candle, fees/slippage, conservative stop-first assumption, full metrics (win rate, net/gross PnL, profit factor, expectancy, max drawdown, fees) with disclosed limitations.
- **Risk engine**: independent of UI and AI. Preflight checks (approval, subscription, emergency stop, fresh prices, balance, exchange filters, position cap, duplicates, stop-risk) run before any order would be placed. Order lifecycle state machine with timeout reconciliation (never blind-resubmit) and protection verification. **Live order execution is not enabled in this version** — the engines exist in `:core` and are exercised by unit tests.
- **Binance integration**: public market data, HMAC-SHA256 signed Futures endpoints, server-time sync, bounded retries with backoff, classified errors (auth/permission/mode/exchange), and a staged connection test that reports public reachability, signed access, Futures permission and account mode separately.
- **Backend**: Supabase Auth, PostgreSQL with RLS on every table, SECURITY DEFINER RPCs audited to `audit_log`, admin approval workflow, one-device-per-user with re-login matching, server-side subscription enforcement, payments that stay pending until admin verification, an AI gateway with ordered model fallback that fails closed, and a signature-verified crypto webhook that activates subscriptions only for correctly signed `finished` payments.
- **AI assistance (Pro)**: server-side gateway; provider keys live only in Edge Function secrets. The app reports real availability, never fakes a response, and AI is advisory only — it cannot change risk limits or authorize anything.

## Non-goals / honest limitations

- No fabricated open interest, funding, liquidation or order-book data — only real exchange data is displayed.
- No live trading in v1. Background work notifies only; it never places orders.
- Manual payments (TRC20/Binance Pay) require admin verification — a screenshot never activates a subscription.
- Automated crypto checkout fails closed until provider secrets are configured.

## Repository layout

```
core/      shared Kotlin engines (no AI, no UI dependencies)
app/       Trading Master (user app)
admin/     Trading Master Admin
pro/       Trading Master Pro
supabase/  migrations/ (SQL) and functions/ (Deno Edge Functions)
.github/   workflows: android-ci.yml, backend-ci.yml, release.yml; dependabot
docs/      SETUP, SECURITY, ADMIN_BOOTSTRAP, RELEASE
```

## Disclaimer

Futures trading involves substantial risk of loss. Nothing in this repository is financial advice, and no scanner score, backtest or AI output is a guarantee of profitability.
