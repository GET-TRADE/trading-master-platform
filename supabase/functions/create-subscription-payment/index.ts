export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};

export const ALLOWED_METHODS = ["binance_pay", "trc20", "manual"];

export function parseJwtSub(authHeader: string | null): string | null {
  if (!authHeader || !authHeader.startsWith("Bearer ")) return null;
  const token = authHeader.split(" ")[1];
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = atob(base64);
    const payload = JSON.parse(jsonPayload);
    return payload.sub || null;
  } catch {
    return null;
  }
}

export function validatePaymentRequest(body: unknown): {
  valid: boolean;
  error?: string;
  data?: { plan_id?: string; method: string; idempotency_key?: string };
} {
  if (typeof body !== "object" || body === null) {
    return { valid: false, error: "Invalid JSON body" };
  }
  const { plan_id, method, idempotency_key } = body as Record<string, unknown>;

  // plan_id is optional: when omitted the server selects the single active plan.
  if (plan_id !== undefined && typeof plan_id !== "string") {
    return { valid: false, error: "plan_id must be a string" };
  }
  if (typeof method !== "string" || !ALLOWED_METHODS.includes(method)) {
    return { valid: false, error: `method must be one of: ${ALLOWED_METHODS.join(", ")}` };
  }

  return {
    valid: true,
    data: {
      plan_id: typeof plan_id === "string" && plan_id.trim() ? plan_id.trim() : undefined,
      method,
      idempotency_key: typeof idempotency_key === "string" && idempotency_key.trim() ? idempotency_key.trim() : undefined,
    },
  };
}

export function extractSettingValue(settingRow: any): string {
  if (!settingRow || settingRow.value === undefined || settingRow.value === null) return "";
  const val = settingRow.value;
  if (typeof val === "string") return val;
  return String(val);
}

if (import.meta.main) {
  Deno.serve(async (req: Request) => {
    if (req.method === "OPTIONS") {
      return new Response("ok", { headers: corsHeaders });
    }

    if (req.method !== "POST") {
      return new Response(JSON.stringify({ ok: false, error: "Method not allowed" }), {
        status: 405,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

    const authHeader = req.headers.get("Authorization");
    const userId = parseJwtSub(authHeader);
    if (!userId) {
      return new Response(JSON.stringify({ ok: false, error: "Missing or invalid authorization token" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    let body: unknown;
    try {
      body = await req.json();
    } catch {
      return new Response(JSON.stringify({ ok: false, error: "Invalid JSON payload" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const validation = validatePaymentRequest(body);
    if (!validation.valid || !validation.data) {
      return new Response(JSON.stringify({ ok: false, error: validation.error }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { plan_id, method, idempotency_key } = validation.data;

    // Load Plan from PostgREST (default: the single active plan when plan_id omitted)
    const planQuery = plan_id ? `id=eq.${plan_id}` : "active=eq.true&order=created_at.asc&limit=1";
    const planRes = await fetch(`${supabaseUrl}/rest/v1/plans?${planQuery}&select=*`, {
      headers: {
        "apikey": serviceKey,
        "Authorization": `Bearer ${serviceKey}`,
      },
    });

    if (!planRes.ok) {
      return new Response(JSON.stringify({ ok: false, error: "Failed to load plan" }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const plans = await planRes.json();
    if (!plans || plans.length === 0 || plans[0].active === false) {
      return new Response(JSON.stringify({ ok: false, error: "Plan not found or inactive" }), {
        status: 404,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const plan = plans[0];
    const amountPkr = plan.price_pkr;

    // Fetch settings for Binance Pay ID & TRC20 address
    const settingsRes = await fetch(`${supabaseUrl}/rest/v1/app_settings?select=key,value`, {
      headers: {
        "apikey": serviceKey,
        "Authorization": `Bearer ${serviceKey}`,
      },
    });

    let binancePayId = "";
    let trc20Address = "";

    if (settingsRes.ok) {
      const settingsList = await settingsRes.json();
      for (const s of settingsList) {
        if (s.key === "binance_pay_id") binancePayId = extractSettingValue(s);
        if (s.key === "trc20_address") trc20Address = extractSettingValue(s);
      }
    }

    // Check Idempotency Key
    if (idempotency_key) {
      const existingRes = await fetch(`${supabaseUrl}/rest/v1/subscription_payments?idempotency_key=eq.${idempotency_key}&select=*`, {
        headers: {
          "apikey": serviceKey,
          "Authorization": `Bearer ${serviceKey}`,
        },
      });

      if (existingRes.ok) {
        const existingList = await existingRes.json();
        if (existingList && existingList.length > 0) {
          const existing = existingList[0];
          return new Response(
            JSON.stringify({
              ok: true,
              payment_id: existing.id,
              amount_pkr: existing.amount_pkr,
              binance_pay_id: binancePayId,
              trc20_address: trc20Address,
            }),
            { headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }
      }
    }

    // Create pending payment
    const createRes = await fetch(`${supabaseUrl}/rest/v1/subscription_payments`, {
      method: "POST",
      headers: {
        "apikey": serviceKey,
        "Authorization": `Bearer ${serviceKey}`,
        "Content-Type": "application/json",
        "Prefer": "return=representation",
      },
      body: JSON.stringify({
        user_id: userId,
        plan_id: plan.id,
        amount_pkr: amountPkr,
        method,
        status: "pending",
        idempotency_key: idempotency_key || null,
      }),
    });

    if (!createRes.ok) {
      const errText = await createRes.text();
      return new Response(JSON.stringify({ ok: false, error: `Failed to create payment record: ${errText}` }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const createdList = await createRes.json();
    const created = createdList[0];

    return new Response(
      JSON.stringify({
        ok: true,
        payment_id: created.id,
        amount_pkr: amountPkr,
        binance_pay_id: binancePayId,
        trc20_address: trc20Address,
      }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  });
}
