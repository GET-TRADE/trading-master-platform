import {
  hasProhibitedKeys,
  getAvailableProviders,
  parseAiAnalysisResponse,
  parseJwtSub,
  AiConfig,
} from "./index.ts";

import { assertEquals, assertNotEquals } from "https://deno.land/std@0.208.0/assert/mod.ts";

Deno.test("hasProhibitedKeys detects credentials in candidate object", () => {
  assertEquals(hasProhibitedKeys({ symbol: "BTCUSDT", price: 50000 }), false);
  assertEquals(hasProhibitedKeys({ symbol: "BTCUSDT", api_key: "secret123" }), true);
  assertEquals(hasProhibitedKeys({ symbol: "BTCUSDT", nested: { binance_secret: "abc" } }), true);
  assertEquals(hasProhibitedKeys({ symbol: "BTCUSDT", Secret: "abc" }), true);
});

Deno.test("getAvailableProviders filters providers based on env vars", () => {
  const mockConfig: AiConfig = {
    providers: [
      { name: "openrouter", models: ["anthropic/claude-3-haiku"], enabled: true },
      { name: "groq", models: ["llama-3.1-70b"], enabled: true },
      { name: "gemini", models: ["gemini-1.5-flash"], enabled: false },
    ],
  };

  const mockEnv = (key: string) => {
    if (key === "OPENROUTER_API_KEY") return "sk-or-test-123";
    return undefined;
  };

  const available = getAvailableProviders(mockConfig, mockEnv);
  assertEquals(available.length, 1);
  assertEquals(available[0].name, "openrouter");
  assertEquals(available[0].models[0], "anthropic/claude-3-haiku");
});

Deno.test("parseAiAnalysisResponse correctly parses JSON structure", () => {
  const jsonInput = JSON.stringify({
    analysis: "Strong bullish trend detected on 4h timeframe.",
    risks: ["High volatility", "Resistance at 52000"],
  });

  const parsed = parseAiAnalysisResponse(jsonInput);
  assertNotEquals(parsed, null);
  assertEquals(parsed?.analysis, "Strong bullish trend detected on 4h timeframe.");
  assertEquals(parsed?.risks.length, 2);
});

Deno.test("parseJwtSub extracts user_id from valid Bearer token", () => {
  // Construct a dummy JWT header + payload
  const header = btoa(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = btoa(JSON.stringify({ sub: "usr_test_12345", exp: 1999999999 }));
  const token = `Bearer ${header}.${payload}.signature_dummy`;

  const userId = parseJwtSub(token);
  assertEquals(userId, "usr_test_12345");
});
