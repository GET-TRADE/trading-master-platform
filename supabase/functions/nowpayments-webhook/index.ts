export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-nowpayments-sig",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export function sortObjectKeys(obj: any): any {
  if (typeof obj !== "object" || obj === null) return obj;
  if (Array.isArray(obj)) return obj.map(sortObjectKeys);
  const sorted: Record<string, any> = {};
  for (const key of Object.keys(obj).sort()) {
    sorted[key] = sortObjectKeys(obj[key]);
  }
  return sorted;
}

export async function computeHmacSha512(secret: string, text: string): Promise<string> {
  const encoder = new TextEncoder();
  const keyData = encoder.encode(secret);
  const key = await crypto.subtle.importKey(
    "raw",
    keyData,
    { name: "HMAC", hash: "SHA-512" },
    false,
    ["sign"]
  );
  const data = encoder.encode(text);
  const signatureBuffer = await crypto.subtle.sign("HMAC", key, data);
  const hashArray = Array.from(new Uint8Array(signatureBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

export async function verifyNowpaymentsSignature(
  secret: string,
  rawBody: string,
  signatureHeader: string | null
): Promise<boolean> {
  if (!secret || !signatureHeader) return false;

  const rawHmac = await computeHmacSha512(secret, rawBody);
  if (rawHmac.toLowerCase() === signatureHeader.trim().toLowerCase()) {
    return true;
  }

  try {
    const parsed = JSON.parse(rawBody);
    const sorted = sortObjectKeys(parsed);
    const sortedJson = JSON.stringify(sorted);
    const sortedHmac = await computeHmacSha512(secret, sortedJson);
    if (sortedHmac.toLowerCase() === signatureHeader.trim().toLowerCase()) {
      return true;
    }
  } catch {
    // raw body not json
  }

  return false;
}

export function isPaymentFinished(status: unknown): boolean {
  if (typeof status !== "string") return false;
  const s = status.toLowerCase();
  return s === "finished" || s === "confirmed";
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

    const secret = Deno.env.get("NOWPAYMENTS_IPN_SECRET");
    if (!secret || secret.trim() === "") {
      return new Response(JSON.stringify({ ok: false, error: "NOWPAYMENTS_IPN_SECRET not configured" }), {
        status: 503,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const rawBody = await req.text();
    const signatureHeader = req.headers.get("x-nowpayments-sig");

    const isValidSig = await verifyNowpaymentsSignature(secret, rawBody, signatureHeader);
    if (!isValidSig) {
      return new Response(JSON.stringify({ ok: false, error: "Invalid signature" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    let payload: any;
    try {
      payload = JSON.parse(rawBody);
    } catch {
      return new Response(JSON.stringify({ ok: false, error: "Invalid JSON body" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const paymentStatus = payload?.payment_status;
    if (!isPaymentFinished(paymentStatus)) {
      return new Response(
        JSON.stringify({ ok: true, message: `Ignored payment status: ${paymentStatus}` }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const orderId = payload?.order_id || payload?.invoice_id || payload?.payment_id;
    const providerRef = String(payload?.payment_id || orderId || "");

    if (!orderId) {
      return new Response(JSON.stringify({ ok: false, error: "Missing order reference in webhook payload" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

    // Find subscription_payment row
    let paymentRecord: any = null;

    // Search by id, idempotency_key, or reference
    const queryRes = await fetch(
      `${supabaseUrl}/rest/v1/subscription_payments?or=(id.eq.${orderId},idempotency_key.eq.${orderId},reference.eq.${providerRef})&select=*`,
      {
        headers: {
          "apikey": serviceKey,
          "Authorization": `Bearer ${serviceKey}`,
        },
      }
    );

    if (queryRes.ok) {
      const rows = await queryRes.json();
      if (rows && rows.length > 0) {
        paymentRecord = rows[0];
      }
    }

    if (!paymentRecord) {
      return new Response(JSON.stringify({ ok: false, error: "Subscription payment record not found" }), {
        status: 404,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Deduplication check
    if (paymentRecord.status === "verified") {
      return new Response(
        JSON.stringify({ ok: true, message: "Payment already processed", payment_id: paymentRecord.id }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Activate subscription via RPC activate_paid_subscription
    const rpcRes = await fetch(`${supabaseUrl}/rest/v1/rpc/activate_paid_subscription`, {
      method: "POST",
      headers: {
        "apikey": serviceKey,
        "Authorization": `Bearer ${serviceKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        payment_id: paymentRecord.id,
        provider_ref: providerRef,
      }),
    });

    if (!rpcRes.ok) {
      const errText = await rpcRes.text();
      return new Response(JSON.stringify({ ok: false, error: `RPC activation failed: ${errText}` }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(
      JSON.stringify({ ok: true, payment_id: paymentRecord.id, status: "verified" }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  });
}
