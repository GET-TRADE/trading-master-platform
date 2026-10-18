export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};

export const PROVIDER_ENV_MAP: Record<string, string> = {
  openrouter: "OPENROUTER_API_KEY",
  groq: "GROQ_API_KEY",
  gemini: "GEMINI_API_KEY",
  openai: "OPENAI_API_KEY",
};

export interface AiProviderConfig {
  name: string;
  models: string[];
  enabled?: boolean;
}

export interface AiConfig {
  providers?: AiProviderConfig[];
  fallback_enabled?: boolean;
}

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

export function hasProhibitedKeys(obj: unknown): boolean {
  if (obj === null || typeof obj !== "object") return false;
  for (const [key, value] of Object.entries(obj as Record<string, unknown>)) {
    if (/api_?key|secret|binance_key|binance_secret|token/i.test(key)) {
      return true;
    }
    if (typeof value === "object" && value !== null) {
      if (hasProhibitedKeys(value)) return true;
    }
  }
  return false;
}

export function getAvailableProviders(
  aiConfig: AiConfig,
  getEnv: (key: string) => string | undefined = (k) => Deno.env.get(k)
): Array<{ name: string; models: string[]; apiKey: string }> {
  const result: Array<{ name: string; models: string[]; apiKey: string }> = [];
  const providers = aiConfig.providers || [];

  for (const p of providers) {
    if (p.enabled === false) continue;
    const envKeyName = PROVIDER_ENV_MAP[p.name.toLowerCase()];
    if (!envKeyName) continue;
    const apiKey = getEnv(envKeyName);
    if (apiKey && apiKey.trim() !== "") {
      result.push({
        name: p.name.toLowerCase(),
        models: p.models || [],
        apiKey: apiKey.trim(),
      });
    }
  }
  return result;
}

export function parseAiAnalysisResponse(rawText: string): { analysis: string; risks: string[] } | null {
  try {
    let clean = rawText.trim();
    if (clean.startsWith("```json")) {
      clean = clean.replace(/^```json\s*/, "").replace(/\s*```$/, "");
    } else if (clean.startsWith("```")) {
      clean = clean.replace(/^```\s*/, "").replace(/\s*```$/, "");
    }
    const parsed = JSON.parse(clean);
    if (typeof parsed === "object" && parsed !== null) {
      const analysis = typeof parsed.analysis === "string" ? parsed.analysis : JSON.stringify(parsed);
      const risks = Array.isArray(parsed.risks) ? parsed.risks.map((r: unknown) => String(r)) : [];
      return { analysis, risks };
    }
  } catch {
    // Return formatted fallback if simple string
    if (rawText.trim().length > 0) {
      return { analysis: rawText.trim(), risks: [] };
    }
  }
  return null;
}

export async function callProviderApi(
  provider: string,
  model: string,
  apiKey: string,
  candidate: unknown,
  timeoutMs = 15000
): Promise<{ analysis: string; risks: string[] } | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  const promptText = `Analyze candidate signal: ${JSON.stringify(candidate)}`;
  const systemPrompt = `You are a crypto trading analyst. Respond strictly in valid JSON format with keys "analysis" (string) and "risks" (array of strings).`;

  try {
    let response: Response;

    if (provider === "gemini") {
      const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;
      response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal: controller.signal,
        body: JSON.stringify({
          contents: [
            {
              parts: [{ text: `${systemPrompt}\n\n${promptText}` }],
            },
          ],
          generationConfig: { responseMimeType: "application/json" },
        }),
      });

      if (!response.ok) return null;
      const json = await response.json();
      const text = json.candidates?.[0]?.content?.parts?.[0]?.text;
      return text ? parseAiAnalysisResponse(text) : null;
    } else {
      let endpoint = "https://api.openai.com/v1/chat/completions";
      if (provider === "openrouter") endpoint = "https://openrouter.ai/api/v1/chat/completions";
      if (provider === "groq") endpoint = "https://api.groq.com/openai/v1/chat/completions";

      response = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${apiKey}`,
        },
        signal: controller.signal,
        body: JSON.stringify({
          model,
          messages: [
            { role: "system", content: systemPrompt },
            { role: "user", content: promptText },
          ],
          response_format: { type: "json_object" },
        }),
      });

      if (!response.ok) return null;
      const json = await response.json();
      const text = json.choices?.[0]?.message?.content;
      return text ? parseAiAnalysisResponse(text) : null;
    }
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

async function verifyUserAccess(supabaseUrl: string, serviceKey: string, userId: string): Promise<boolean> {
  try {
    const res = await fetch(`${supabaseUrl}/rest/v1/app_access?user_id=eq.${userId}&select=status,subscription_expires_at`, {
      headers: {
        "apikey": serviceKey,
        "Authorization": `Bearer ${serviceKey}`,
      },
    });
    if (!res.ok) return false;
    const rows: Array<{ status: string; subscription_expires_at: string | null }> = await res.json();
    const now = new Date();

    return rows.some((row) => {
      if (row.status !== "approved") return false;
      if (!row.subscription_expires_at) return true;
      return new Date(row.subscription_expires_at) > now;
    });
  } catch {
    return false;
  }
}

async function fetchAiConfig(supabaseUrl: string, serviceKey: string): Promise<AiConfig> {
  try {
    const res = await fetch(`${supabaseUrl}/rest/v1/app_settings?key=eq.ai_config&select=value`, {
      headers: {
        "apikey": serviceKey,
        "Authorization": `Bearer ${serviceKey}`,
      },
    });
    if (!res.ok) return { providers: [], fallback_enabled: true };
    const rows = await res.json();
    if (rows && rows.length > 0 && rows[0].value) {
      return rows[0].value as AiConfig;
    }
  } catch {
    // return default
  }
  return { providers: [], fallback_enabled: true };
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

    const isAuthorized = await verifyUserAccess(supabaseUrl, serviceKey, userId);
    if (!isAuthorized) {
      return new Response(JSON.stringify({ ok: false, error: "Unauthorized or inactive subscription" }), {
        status: 403,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    let body: any;
    try {
      body = await req.json();
    } catch {
      return new Response(JSON.stringify({ ok: false, error: "Invalid JSON body" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const action = body?.action;

    if (action === "health") {
      const configuredProviders = Object.keys(PROVIDER_ENV_MAP).filter((name) => {
        const envVar = PROVIDER_ENV_MAP[name];
        const val = Deno.env.get(envVar);
        return val && val.trim() !== "";
      });

      return new Response(
        JSON.stringify({
          ok: true,
          available: configuredProviders.length > 0,
          providers: configuredProviders,
          detail: configuredProviders.length > 0 ? "AI gateway ready" : "No AI provider secrets configured",
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (action === "analyze") {
      const candidate = body?.candidate;
      if (!candidate) {
        return new Response(JSON.stringify({ ok: false, error: "Missing candidate signal payload" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      if (hasProhibitedKeys(candidate)) {
        return new Response(JSON.stringify({ ok: false, error: "Request candidate contains prohibited key fields" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      const aiConfig = await fetchAiConfig(supabaseUrl, serviceKey);
      const availableProviders = getAvailableProviders(aiConfig);

      if (availableProviders.length === 0) {
        return new Response(JSON.stringify({ ok: false, error: "all providers failed" }), {
          status: 503,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      for (let i = 0; i < availableProviders.length; i++) {
        const provider = availableProviders[i];
        const isFallback = i > 0;

        for (const model of provider.models) {
          const result = await callProviderApi(provider.name, model, provider.apiKey, candidate, 15000);
          if (result) {
            return new Response(
              JSON.stringify({
                ok: true,
                model_used: `${provider.name}/${model}`,
                analysis: result.analysis,
                risks: result.risks,
                fallback: isFallback,
              }),
              { headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
          }
        }
      }

      return new Response(JSON.stringify({ ok: false, error: "all providers failed" }), {
        status: 502,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ ok: false, error: "Unknown action" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  });
}
