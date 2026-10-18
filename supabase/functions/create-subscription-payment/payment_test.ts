import { validatePaymentRequest, extractSettingValue } from "./index.ts";
import { assertEquals } from "https://deno.land/std@0.208.0/assert/mod.ts";

Deno.test("validatePaymentRequest validates payment input payload", () => {
  const invalidMethod = validatePaymentRequest({ plan_id: "plan_123", method: "invalid_method" });
  assertEquals(invalidMethod.valid, false);

  const missingPlan = validatePaymentRequest({ method: "binance_pay" });
  assertEquals(missingPlan.valid, false);

  const validReq = validatePaymentRequest({
    plan_id: "plan_123",
    method: "trc20",
    idempotency_key: "idem_999",
  });
  assertEquals(validReq.valid, true);
  assertEquals(validReq.data?.plan_id, "plan_123");
  assertEquals(validReq.data?.method, "trc20");
  assertEquals(validReq.data?.idempotency_key, "idem_999");
});

Deno.test("extractSettingValue extracts string values from setting rows", () => {
  assertEquals(extractSettingValue({ value: "12345678" }), "12345678");
  assertEquals(extractSettingValue({ value: null }), "");
  assertEquals(extractSettingValue(null), "");
});
