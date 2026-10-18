export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

export const AI_SECRET_ENV_KEYS = [
  "OPENROUTER_API_KEY",
  "GROQ_API_KEY",
  "GEMINI_API_KEY",
  "OPENAI_API_KEY",
];

export function checkAiConfigured(getEnv: (key: string) => string | undefined = (k) => Deno.env.get(k)): boolean {
  return AI_SECRET_ENV_KEYS.some((key) => {
    const val = getEnv(key);
    return typeof val === "string" && val.trim() !== "";
  });
}

export function computeHealthStatus(dbReachable: boolean): "ok" | "degraded" {
  return dbReachable ? "ok" : "degraded";
}

if (import.meta.main) {
  Deno.serve(async (req: Request) => {
    if (req.method === "OPTIONS") {
      return new Response("ok", { headers: corsHeaders });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

    let dbReachable = false;

    if (supabaseUrl && serviceKey) {
      try {
        const res = await fetch(`${supabaseUrl}/rest/v1/app_settings?select=key&limit=1`, {
          headers: {
            "apikey": serviceKey,
            "Authorization": `Bearer ${serviceKey}`,
          },
        });
        if (res.ok) {
          dbReachable = true;
        }
      } catch {
        dbReachable = false;
      }
    }

    const aiConfigured = checkAiConfigured();
    const status = computeHealthStatus(dbReachable);

    return new Response(
      JSON.stringify({
        status,
        db: dbReachable,
        ai_configured: aiConfigured,
        version: "0.1.0",
      }),
      {
        status: dbReachable ? 200 : 503,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  });
}
