-- supabase/migrations/0002_seed_default_plan.sql
-- Seed Default Plan and Default App Settings

INSERT INTO public.plans (name, price_pkr, duration_days, features, active)
VALUES (
  'Monthly — PKR 5,000',
  5000,
  30,
  '["scanner","signals","history","paper_journal"]'::jsonb,
  true
)
ON CONFLICT DO NOTHING;

INSERT INTO public.app_settings (key, value)
VALUES
  ('binance_pay_id', '""'::jsonb),
  ('trc20_address', '""'::jsonb),
  ('ai_config', '{"providers":[], "fallback_enabled":true}'::jsonb)
ON CONFLICT (key) DO NOTHING;
