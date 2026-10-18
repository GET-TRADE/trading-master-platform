-- supabase/migrations/0001_init.sql
-- Trading Master Platform Initial Schema Migration

-- Enable pgcrypto if needed for UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. profiles
CREATE TABLE IF NOT EXISTS public.profiles (
  id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email text,
  whatsapp text,
  display_name text,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- 2. admin_users
CREATE TABLE IF NOT EXISTS public.admin_users (
  id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email text UNIQUE,
  role text NOT NULL CHECK (role IN ('super_admin', 'admin')),
  created_by uuid NULL REFERENCES auth.users(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

-- 3. app_access
CREATE TABLE IF NOT EXISTS public.app_access (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  app_key text NOT NULL CHECK (app_key IN ('tm-user', 'tm-pro')),
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'blocked', 'rejected')),
  plan_id uuid NULL,
  subscription_expires_at timestamptz NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id, app_key)
);

-- 4. devices
CREATE TABLE IF NOT EXISTS public.devices (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  app_key text NOT NULL,
  device_key text NOT NULL,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'revoked')),
  approved_at timestamptz NULL,
  revoked_at timestamptz NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz DEFAULT now(),
  UNIQUE(user_id, app_key, device_key)
);

-- 5. plans
CREATE TABLE IF NOT EXISTS public.plans (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text,
  price_pkr numeric NOT NULL,
  duration_days int NOT NULL,
  features jsonb NOT NULL DEFAULT '[]'::jsonb,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- 6. subscription_payments
CREATE TABLE IF NOT EXISTS public.subscription_payments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  plan_id uuid REFERENCES public.plans(id) ON DELETE SET NULL,
  amount_pkr numeric,
  method text CHECK (method IN ('binance_pay', 'trc20', 'manual', 'nowpayments')),
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'verified', 'rejected', 'refunded')),
  reference text,
  proof_note text,
  idempotency_key text UNIQUE,
  verified_at timestamptz,
  verified_by uuid REFERENCES auth.users(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

-- 7. app_settings
CREATE TABLE IF NOT EXISTS public.app_settings (
  key text PRIMARY KEY,
  value jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

-- 8. audit_log
CREATE TABLE IF NOT EXISTS public.audit_log (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_id uuid,
  action text NOT NULL,
  target_user_id uuid NULL,
  details jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- 9. error_events
CREATE TABLE IF NOT EXISTS public.error_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NULL,
  source text NOT NULL,
  message text NOT NULL,
  payload jsonb NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

--------------------------------------------------------------------------------
-- SECURITY DEFINER RPC FUNCTIONS (ADMIN)
--------------------------------------------------------------------------------

-- Helper for Admin Verification + Audit Logging
CREATE OR REPLACE FUNCTION public._verify_admin_and_audit(p_action text, p_target_user_id uuid DEFAULT NULL, p_details jsonb DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  IF auth.uid() IS NULL OR NOT EXISTS (SELECT 1 FROM public.admin_users WHERE id = auth.uid()) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  INSERT INTO public.audit_log (actor_id, action, target_user_id, details)
  VALUES (auth.uid(), p_action, p_target_user_id, p_details);
END;
$$;

-- is_admin
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  RETURN EXISTS (
    SELECT 1 FROM public.admin_users WHERE id = auth.uid()
  );
END;
$$;

-- admin_list_users
CREATE OR REPLACE FUNCTION public.admin_list_users()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  PERFORM public._verify_admin_and_audit('admin_list_users', NULL, NULL);

  RETURN (
    SELECT coalesce(jsonb_agg(
      jsonb_build_object(
        'id', p.id,
        'email', p.email,
        'whatsapp', p.whatsapp,
        'display_name', p.display_name,
        'created_at', p.created_at,
        'app_access', (
          SELECT coalesce(jsonb_agg(jsonb_build_object(
            'id', aa.id,
            'app_key', aa.app_key,
            'status', aa.status,
            'plan_id', aa.plan_id,
            'subscription_expires_at', aa.subscription_expires_at,
            'updated_at', aa.updated_at
          )), '[]'::jsonb)
          FROM public.app_access aa WHERE aa.user_id = p.id
        ),
        'devices', (
          SELECT coalesce(jsonb_agg(jsonb_build_object(
            'id', d.id,
            'app_key', d.app_key,
            'device_key', d.device_key,
            'status', d.status,
            'approved_at', d.approved_at,
            'revoked_at', d.revoked_at,
            'updated_at', d.updated_at
          )), '[]'::jsonb)
          FROM public.devices d WHERE d.user_id = p.id
        )
      )
    ), '[]'::jsonb)
    FROM public.profiles p
  );
END;
$$;

-- admin_set_user_status
CREATE OR REPLACE FUNCTION public.admin_set_user_status(target_user_id uuid, app_key text, new_status text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  PERFORM public._verify_admin_and_audit('admin_set_user_status', target_user_id, jsonb_build_object('app_key', app_key, 'new_status', new_status));

  UPDATE public.app_access
  SET status = new_status, updated_at = now()
  WHERE user_id = target_user_id AND app_access.app_key = admin_set_user_status.app_key;
END;
$$;

-- admin_delete_user
CREATE OR REPLACE FUNCTION public.admin_delete_user(target_user_id uuid, app_key text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  PERFORM public._verify_admin_and_audit('admin_delete_user', target_user_id, jsonb_build_object('app_key', app_key));

  DELETE FROM public.devices WHERE user_id = target_user_id AND devices.app_key = admin_delete_user.app_key;
  DELETE FROM public.app_access WHERE user_id = target_user_id AND app_access.app_key = admin_delete_user.app_key;
END;
$$;

-- admin_approve_device
CREATE OR REPLACE FUNCTION public.admin_approve_device(device_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_user_id uuid;
BEGIN
  SELECT user_id INTO v_user_id FROM public.devices WHERE id = device_id;
  PERFORM public._verify_admin_and_audit('admin_approve_device', v_user_id, jsonb_build_object('device_id', device_id));

  UPDATE public.devices
  SET status = 'approved', approved_at = now(), updated_at = now()
  WHERE id = device_id;
END;
$$;

-- admin_revoke_device
CREATE OR REPLACE FUNCTION public.admin_revoke_device(device_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_user_id uuid;
BEGIN
  SELECT user_id INTO v_user_id FROM public.devices WHERE id = device_id;
  PERFORM public._verify_admin_and_audit('admin_revoke_device', v_user_id, jsonb_build_object('device_id', device_id));

  UPDATE public.devices
  SET status = 'revoked', revoked_at = now(), updated_at = now()
  WHERE id = device_id;
END;
$$;

-- admin_set_subscription
CREATE OR REPLACE FUNCTION public.admin_set_subscription(target_user_id uuid, app_key text, plan_id uuid, days int)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  PERFORM public._verify_admin_and_audit('admin_set_subscription', target_user_id, jsonb_build_object('app_key', app_key, 'plan_id', plan_id, 'days', days));

  UPDATE public.app_access
  SET plan_id = admin_set_subscription.plan_id,
      subscription_expires_at = now() + (days || ' days')::interval,
      status = 'approved',
      updated_at = now()
  WHERE user_id = target_user_id AND app_access.app_key = admin_set_subscription.app_key;
END;
$$;

-- admin_list_plans
CREATE OR REPLACE FUNCTION public.admin_list_plans()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  PERFORM public._verify_admin_and_audit('admin_list_plans', NULL, NULL);

  RETURN (
    SELECT coalesce(jsonb_agg(to_jsonb(p)), '[]'::jsonb)
    FROM public.plans p
  );
END;
$$;

-- admin_create_plan
CREATE OR REPLACE FUNCTION public.admin_create_plan(name text, price_pkr numeric, duration_days int, features jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_plan public.plans%ROWTYPE;
BEGIN
  PERFORM public._verify_admin_and_audit('admin_create_plan', NULL, jsonb_build_object('name', name, 'price_pkr', price_pkr));

  INSERT INTO public.plans (name, price_pkr, duration_days, features, active)
  VALUES (name, price_pkr, duration_days, coalesce(features, '[]'::jsonb), true)
  RETURNING * INTO v_plan;

  RETURN to_jsonb(v_plan);
END;
$$;

-- admin_update_plan
CREATE OR REPLACE FUNCTION public.admin_update_plan(plan_id uuid, name text, price_pkr numeric, duration_days int, features jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_plan public.plans%ROWTYPE;
BEGIN
  PERFORM public._verify_admin_and_audit('admin_update_plan', NULL, jsonb_build_object('plan_id', plan_id));

  UPDATE public.plans
  SET name = coalesce(admin_update_plan.name, plans.name),
      price_pkr = coalesce(admin_update_plan.price_pkr, plans.price_pkr),
      duration_days = coalesce(admin_update_plan.duration_days, plans.duration_days),
      features = coalesce(admin_update_plan.features, plans.features)
  WHERE id = plan_id
  RETURNING * INTO v_plan;

  RETURN to_jsonb(v_plan);
END;
$$;

-- admin_set_setting
CREATE OR REPLACE FUNCTION public.admin_set_setting(key text, value jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  PERFORM public._verify_admin_and_audit('admin_set_setting', NULL, jsonb_build_object('key', key, 'value', value));

  INSERT INTO public.app_settings (key, value, updated_at)
  VALUES (key, value, now())
  ON CONFLICT (key) DO UPDATE
  SET value = EXCLUDED.value, updated_at = now();
END;
$$;

-- admin_get_settings
CREATE OR REPLACE FUNCTION public.admin_get_settings()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_binance text;
  v_trc20 text;
  v_ai jsonb;
BEGIN
  PERFORM public._verify_admin_and_audit('admin_get_settings', NULL, NULL);

  SELECT (CASE WHEN jsonb_typeof(value) = 'string' THEN value#>>'{}' ELSE value::text END) INTO v_binance FROM public.app_settings WHERE key = 'binance_pay_id';
  SELECT (CASE WHEN jsonb_typeof(value) = 'string' THEN value#>>'{}' ELSE value::text END) INTO v_trc20 FROM public.app_settings WHERE key = 'trc20_address';
  SELECT value INTO v_ai FROM public.app_settings WHERE key = 'ai_config';

  RETURN jsonb_build_object(
    'binance_pay_id', coalesce(v_binance, ''),
    'trc20_address', coalesce(v_trc20, ''),
    'ai_config', coalesce(v_ai, '{"providers":[],"fallback_enabled":true}'::jsonb)
  );
END;
$$;

-- admin_verify_payment
CREATE OR REPLACE FUNCTION public.admin_verify_payment(payment_id uuid, verified boolean, reference text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_user_id uuid;
  v_plan_id uuid;
  v_days int;
BEGIN
  SELECT user_id, plan_id INTO v_user_id, v_plan_id
  FROM public.subscription_payments
  WHERE id = payment_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'payment not found';
  END IF;

  PERFORM public._verify_admin_and_audit('admin_verify_payment', v_user_id, jsonb_build_object('payment_id', payment_id, 'verified', verified, 'reference', reference));

  IF verified THEN
    UPDATE public.subscription_payments
    SET status = 'verified',
        verified_at = now(),
        verified_by = auth.uid(),
        reference = coalesce(admin_verify_payment.reference, subscription_payments.reference)
    WHERE id = payment_id;

    SELECT duration_days INTO v_days FROM public.plans WHERE id = v_plan_id;
    IF v_days IS NOT NULL THEN
      UPDATE public.app_access
      SET status = 'approved',
          plan_id = v_plan_id,
          subscription_expires_at = GREATEST(coalesce(subscription_expires_at, now()), now()) + (v_days || ' days')::interval,
          updated_at = now()
      WHERE user_id = v_user_id AND app_key = 'tm-pro';
    END IF;
  ELSE
    UPDATE public.subscription_payments
    SET status = 'rejected',
        verified_at = now(),
        verified_by = auth.uid()
    WHERE id = payment_id;
  END IF;
END;
$$;

-- admin_list_payments
CREATE OR REPLACE FUNCTION public.admin_list_payments()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  PERFORM public._verify_admin_and_audit('admin_list_payments', NULL, NULL);

  RETURN (
    SELECT coalesce(jsonb_agg(
      to_jsonb(sp) || jsonb_build_object('user_email', p.email)
    ), '[]'::jsonb)
    FROM public.subscription_payments sp
    LEFT JOIN public.profiles p ON p.id = sp.user_id
    ORDER BY sp.created_at DESC
  );
END;
$$;

-- admin_get_audit
CREATE OR REPLACE FUNCTION public.admin_get_audit(limit_val int DEFAULT 100)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  PERFORM public._verify_admin_and_audit('admin_get_audit', NULL, jsonb_build_object('limit', limit_val));

  RETURN (
    SELECT coalesce(jsonb_agg(to_jsonb(a)), '[]'::jsonb)
    FROM (
      SELECT * FROM public.audit_log
      ORDER BY created_at DESC
      LIMIT limit_val
    ) a
  );
END;
$$;

-- admin_get_errors
CREATE OR REPLACE FUNCTION public.admin_get_errors(limit_val int DEFAULT 100)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  PERFORM public._verify_admin_and_audit('admin_get_errors', NULL, jsonb_build_object('limit', limit_val));

  RETURN (
    SELECT coalesce(jsonb_agg(to_jsonb(e)), '[]'::jsonb)
    FROM (
      SELECT * FROM public.error_events
      ORDER BY created_at DESC
      LIMIT limit_val
    ) e
  );
END;
$$;

--------------------------------------------------------------------------------
-- USER-SIDE SECURITY DEFINER RPC FUNCTIONS
--------------------------------------------------------------------------------

-- register_device
CREATE OR REPLACE FUNCTION public.register_device(device_key text, app_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid uuid;
  v_existing_status text;
BEGIN
  v_uid := auth.uid();
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'authenticated user required';
  END IF;

  SELECT status INTO v_existing_status
  FROM public.devices
  WHERE user_id = v_uid AND devices.app_key = register_device.app_key AND devices.device_key = register_device.device_key;

  IF FOUND THEN
    IF v_existing_status = 'approved' THEN
      UPDATE public.devices
      SET updated_at = now()
      WHERE user_id = v_uid AND devices.app_key = register_device.app_key AND devices.device_key = register_device.device_key;

      RETURN jsonb_build_object(
        'ok', true,
        'device_status', 'approved',
        'matched_approved', true
      );
    ELSE
      UPDATE public.devices
      SET updated_at = now()
      WHERE user_id = v_uid AND devices.app_key = register_device.app_key AND devices.device_key = register_device.device_key;

      RETURN jsonb_build_object(
        'ok', true,
        'device_status', v_existing_status,
        'matched_approved', false
      );
    END IF;
  ELSE
    INSERT INTO public.devices (user_id, app_key, device_key, status, updated_at)
    VALUES (v_uid, register_device.app_key, register_device.device_key, 'pending', now());

    RETURN jsonb_build_object(
      'ok', true,
      'device_status', 'pending',
      'matched_approved', false
    );
  END IF;
END;
$$;

-- get_my_access
CREATE OR REPLACE FUNCTION public.get_my_access(app_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid uuid;
  v_rec public.app_access%ROWTYPE;
BEGIN
  v_uid := auth.uid();
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'authenticated user required';
  END IF;

  SELECT * INTO v_rec
  FROM public.app_access
  WHERE user_id = v_uid AND app_access.app_key = get_my_access.app_key;

  IF NOT FOUND THEN
    RETURN jsonb_build_object(
      'id', NULL,
      'user_id', v_uid,
      'app_key', get_my_access.app_key,
      'status', 'pending',
      'plan_id', NULL,
      'subscription_expires_at', NULL,
      'subscription_active', false,
      'created_at', NULL,
      'updated_at', NULL
    );
  END IF;

  RETURN jsonb_build_object(
    'id', v_rec.id,
    'user_id', v_rec.user_id,
    'app_key', v_rec.app_key,
    'status', v_rec.status,
    'plan_id', v_rec.plan_id,
    'subscription_expires_at', v_rec.subscription_expires_at,
    'subscription_active', (v_rec.subscription_expires_at IS NOT NULL AND v_rec.subscription_expires_at > now()),
    'created_at', v_rec.created_at,
    'updated_at', v_rec.updated_at
  );
END;
$$;

-- report_error_event
CREATE OR REPLACE FUNCTION public.report_error_event(source text, message text, payload jsonb DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid uuid;
BEGIN
  v_uid := auth.uid();
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'authenticated user required';
  END IF;

  INSERT INTO public.error_events (user_id, source, message, payload)
  VALUES (v_uid, source, message, payload);
END;
$$;

--------------------------------------------------------------------------------
-- SERVICE-ROLE SPECIAL RPC FOR NOWPAYMENTS WEBHOOK
--------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.activate_paid_subscription(payment_id uuid, provider_ref text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_user_id uuid;
  v_plan_id uuid;
  v_days int;
BEGIN
  SELECT user_id, plan_id INTO v_user_id, v_plan_id
  FROM public.subscription_payments
  WHERE id = payment_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'payment record not found';
  END IF;

  UPDATE public.subscription_payments
  SET status = 'verified',
      verified_at = now(),
      reference = coalesce(provider_ref, reference)
  WHERE id = payment_id;

  SELECT duration_days INTO v_days FROM public.plans WHERE id = v_plan_id;
  IF v_days IS NOT NULL THEN
    UPDATE public.app_access
    SET status = 'approved',
        plan_id = v_plan_id,
        subscription_expires_at = GREATEST(coalesce(subscription_expires_at, now()), now()) + (v_days || ' days')::interval,
        updated_at = now()
    WHERE user_id = v_user_id AND app_key = 'tm-pro';
  END IF;
END;
$$;

--------------------------------------------------------------------------------
-- AUTH USER CREATION TRIGGER
--------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_whatsapp text;
BEGIN
  v_whatsapp := new.raw_user_meta_data->>'whatsapp';

  INSERT INTO public.profiles (id, email, whatsapp, display_name)
  VALUES (
    new.id,
    new.email,
    v_whatsapp,
    coalesce(new.raw_user_meta_data->>'display_name', new.raw_user_meta_data->>'full_name', split_part(new.email, '@', 1))
  )
  ON CONFLICT (id) DO UPDATE
  SET email = EXCLUDED.email,
      whatsapp = coalesce(EXCLUDED.whatsapp, profiles.whatsapp);

  INSERT INTO public.app_access (user_id, app_key, status)
  VALUES
    (new.id, 'tm-user', 'pending'),
    (new.id, 'tm-pro', 'pending')
  ON CONFLICT (user_id, app_key) DO NOTHING;

  RETURN new;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

--------------------------------------------------------------------------------
-- ROW LEVEL SECURITY & POLICIES
--------------------------------------------------------------------------------

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_access ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subscription_payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.error_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY profiles_select_own ON public.profiles
  FOR SELECT TO authenticated
  USING (auth.uid() = id);

CREATE POLICY app_access_select_own ON public.app_access
  FOR SELECT TO authenticated
  USING (auth.uid() = user_id);

CREATE POLICY devices_select_own ON public.devices
  FOR SELECT TO authenticated
  USING (auth.uid() = user_id);

CREATE POLICY subscription_payments_select_own ON public.subscription_payments
  FOR SELECT TO authenticated
  USING (auth.uid() = user_id);

CREATE POLICY plans_select_active ON public.plans
  FOR SELECT TO authenticated
  USING (active = true);

-- Grant executions
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO authenticated;
GRANT EXECUTE ON FUNCTION public.activate_paid_subscription(uuid, text) TO service_role;
