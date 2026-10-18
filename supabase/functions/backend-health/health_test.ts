import { checkAiConfigured, computeHealthStatus } from "./index.ts";
import { assertEquals } from "https://deno.land/std@0.208.0/assert/mod.ts";

Deno.test("checkAiConfigured detects when at least one secret is set", () => {
  const mockEnvWithKey = (key: string) => (key === "GROQ_API_KEY" ? "gsk_test123" : undefined);
  assertEquals(checkAiConfigured(mockEnvWithKey), true);

  const mockEnvEmpty = () => undefined;
  assertEquals(checkAiConfigured(mockEnvEmpty), false);
});

Deno.test("computeHealthStatus returns ok when db is reachable", () => {
  assertEquals(computeHealthStatus(true), "ok");
  assertEquals(computeHealthStatus(false), "degraded");
});
