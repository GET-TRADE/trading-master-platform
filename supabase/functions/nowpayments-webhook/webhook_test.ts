import {
  computeHmacSha512,
  verifyNowpaymentsSignature,
  isPaymentFinished,
  sortObjectKeys,
} from "./index.ts";

import { assertEquals } from "https://deno.land/std@0.208.0/assert/mod.ts";

Deno.test("computeHmacSha512 generates correct signature", async () => {
  const secret = "test_ipn_secret_123";
  const text = '{"payment_id":12345,"payment_status":"finished"}';
  const sig = await computeHmacSha512(secret, text);

  const isValid = await verifyNowpaymentsSignature(secret, text, sig);
  assertEquals(isValid, true);
});

Deno.test("verifyNowpaymentsSignature rejects wrong secret or altered payload", async () => {
  const secret = "correct_secret";
  const text = '{"payment_id":12345,"payment_status":"finished"}';
  const sig = await computeHmacSha512(secret, text);

  const wrongSecretCheck = await verifyNowpaymentsSignature("wrong_secret", text, sig);
  assertEquals(wrongSecretCheck, false);

  const alteredPayloadCheck = await verifyNowpaymentsSignature(secret, '{"payment_id":999}', sig);
  assertEquals(alteredPayloadCheck, false);
});

Deno.test("isPaymentFinished recognizes finished statuses", () => {
  assertEquals(isPaymentFinished("finished"), true);
  assertEquals(isPaymentFinished("confirmed"), true);
  assertEquals(isPaymentFinished("FINISHED"), true);
  assertEquals(isPaymentFinished("waiting"), false);
  assertEquals(isPaymentFinished("failed"), false);
});

Deno.test("sortObjectKeys recursively sorts keys alphabetically", () => {
  const input = { z: 1, a: { c: 2, b: 3 } };
  const sorted = sortObjectKeys(input);
  assertEquals(Object.keys(sorted), ["a", "z"]);
  assertEquals(Object.keys(sorted.a), ["b", "c"]);
});
