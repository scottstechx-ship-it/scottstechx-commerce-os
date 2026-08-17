/**
 * Tests for the payment + auth additions:
 *   - POST /api/v1/payments/collect (demo mode) advances created -> paid -> assigned
 *   - POST /api/v1/payments/webhook rejects unsigned payloads
 *   - POST /api/v1/auth/register (phone + password signup)
 *   - POST /api/v1/auth/firebase 503 when Firebase is not configured
 *   - AI provider selection (nvidia)
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import {
  setup as setupOnce,
  teardown as teardownOnce,
  getBaseUrl,
  SEED,
  mintToken,
  randomIdempotencyKey,
} from "./setup.js";
import { query } from "../src/db.js";
import { getProvider } from "../src/modules/ai/assistant.service.js";
import { getPaymentMode } from "../src/modules/payments/payments.service.js";

let baseUrl = "";
let buyerToken = "";
let driverToken = "";

beforeAll(async () => {
  await setupOnce();
  baseUrl = getBaseUrl();
  buyerToken = await mintToken("buyer", SEED.buyerId);
  driverToken = await mintToken("driver", SEED.driverId);
  process.env.PAYMENT_PROVIDER = "demo";
});

afterAll(async () => {
  await teardownOnce();
});

beforeEach(async () => {
  await query(`TRUNCATE payments, order_items, orders, audit_logs, idempotency_keys RESTART IDENTITY CASCADE`);
  await query(`UPDATE products SET stock_quantity = 25 WHERE id = $1`, [SEED.productA]);
});

async function postJson<T = unknown>(
  path: string,
  body: unknown,
  headers: Record<string, string> = {},
): Promise<{ status: number; body: T }> {
  const res = await fetch(`${baseUrl}${path}`, {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  let parsed: unknown = text;
  try {
    parsed = JSON.parse(text);
  } catch {
    /* not JSON */
  }
  return { status: res.status, body: parsed as T };
}

async function getJson<T = unknown>(
  path: string,
  headers: Record<string, string> = {},
): Promise<{ status: number; body: T }> {
  const res = await fetch(`${baseUrl}${path}`, { headers });
  const text = await res.text();
  let parsed: unknown = text;
  try {
    parsed = JSON.parse(text);
  } catch {
    /* not JSON */
  }
  return { status: res.status, body: parsed as T };
}

async function createOrder(): Promise<string> {
  const co = await postJson<{ orderId: string }>(
    "/api/v1/orders/checkout",
    {
      items: [{ productId: SEED.productA, qty: 1 }],
      deliveryAddress: { line1: "Plot 1", city: "Kampala", country: "UG" },
    },
    { authorization: `Bearer ${buyerToken}`, "idempotency-key": randomIdempotencyKey() },
  );
  expect(co.status).toBe(201);
  return co.body.orderId;
}

describe("POST /api/v1/payments/collect (demo mode)", () => {
  it("collects payment and advances the order to an assigned driver", async () => {
    const orderId = await createOrder();

    const pay = await postJson<{ reference: string; status: string; provider: string }>(
      "/api/v1/payments/collect",
      { orderId },
      { authorization: `Bearer ${buyerToken}` },
    );
    expect(pay.status).toBe(200);
    expect(pay.body.provider).toBe("demo");
    expect(pay.body.status).toBe("successful");
    expect(pay.body.reference).toMatch(/^[0-9a-f-]{36}$/);

    // The order must now be visible to the demo driver as assigned.
    const assigned = await getJson<Array<{ orderId: string; status: string }>>(
      "/api/v1/logistics/assigned",
      { authorization: `Bearer ${driverToken}` },
    );
    expect(assigned.status).toBe(200);
    expect(assigned.body).toHaveLength(1);
    expect(assigned.body[0]!.orderId).toBe(orderId);
    expect(assigned.body[0]!.status).toBe("assigned");
  });

  it("rejects a second collect on an already-paid order (409)", async () => {
    const orderId = await createOrder();
    await postJson("/api/v1/payments/collect", { orderId }, { authorization: `Bearer ${buyerToken}` });
    const again = await postJson("/api/v1/payments/collect", { orderId }, { authorization: `Bearer ${buyerToken}` });
    expect(again.status).toBe(409);
  });

  it("does not let a buyer collect another buyer's order (404)", async () => {
    const orderId = await createOrder();
    const otherToken = await mintToken("buyer", "99999999-9999-4999-8999-999999999999");
    const r = await postJson("/api/v1/payments/collect", { orderId }, { authorization: `Bearer ${otherToken}` });
    expect(r.status).toBe(404);
  });

  it("requires buyer auth", async () => {
    const orderId = await createOrder();
    const r = await postJson("/api/v1/payments/collect", { orderId }, { authorization: `Bearer ${driverToken}` });
    expect(r.status).toBe(403);
  });
});

describe("POST /api/v1/payments/webhook", () => {
  it("rejects unsigned webhooks with 401 (no webhook secret configured)", async () => {
    const r = await postJson("/api/v1/payments/webhook", {
      event: "transaction.successful",
      payload: { reference: "11111111-1111-4111-8111-111111111111", status: "successful" },
    });
    expect(r.status).toBe(401);
  });
});

describe("POST /api/v1/auth/register", () => {
  it("signs up a new phone user and lets them log in", async () => {
    const phone = "+256700000077";
    const reg = await postJson<{ token: string; userId: string; role: string }>(
      "/api/v1/auth/register",
      { phone, password: "password123", displayName: "New Buyer" },
    );
    expect(reg.status).toBe(201);
    expect(reg.body.role).toBe("buyer");
    expect(typeof reg.body.token).toBe("string");

    const login = await postJson<{ userId: string }>("/api/v1/auth/login", {
      phone,
      password: "password123",
    });
    expect(login.status).toBe(200);
    expect(login.body.userId).toBe(reg.body.userId);
  });

  it("rejects a duplicate phone with 409", async () => {
    await postJson("/api/v1/auth/register", { phone: "+256700000088", password: "password123" });
    const dup = await postJson("/api/v1/auth/register", { phone: "+256700000088", password: "password123" });
    expect(dup.status).toBe(409);
  });

  it("rejects a short password with 400", async () => {
    const r = await postJson("/api/v1/auth/register", { phone: "+256700000099", password: "short" });
    expect(r.status).toBe(400);
  });
});

describe("POST /api/v1/auth/firebase", () => {
  it("returns 503 when Firebase is not configured", async () => {
    const r = await postJson("/api/v1/auth/firebase", { idToken: "fake-id-token-0000000000000000" });
    expect(r.status).toBe(503);
    expect((r.body as { error?: string }).error).toBe("firebase_auth_disabled");
  });
});

describe("AI provider selection", () => {
  it("returns nvidia when AI_PROVIDER=nvidia and NVIDIA_API_KEY is set", () => {
    process.env.AI_PROVIDER = "nvidia";
    process.env.NVIDIA_API_KEY = "nvapi-test";
    expect(getProvider()).toBe("nvidia");
    process.env.AI_PROVIDER = "";
    process.env.NVIDIA_API_KEY = "";
  });

  it("returns null when nothing is configured", () => {
    expect(getProvider()).toBe(null);
  });
});

describe("payment mode selection", () => {
  it("is demo when PAYMENT_PROVIDER=demo", () => {
    expect(getPaymentMode()).toBe("demo");
  });
});
