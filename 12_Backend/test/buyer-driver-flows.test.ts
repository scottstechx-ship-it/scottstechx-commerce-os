/**
 * End-to-end tests for the buyer + driver MVP flows that the Android client
 * depends on:
 *   - POST /api/v1/auth/login   (phone + password)
 *   - GET  /api/v1/products     (buyer catalog)
 *   - GET  /api/v1/logistics/assigned (driver job list)
 *
 * Run against the real embedded Postgres + Fastify server (see test/setup.ts).
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

let baseUrl = "";
let buyerToken = "";
let driverToken = "";

beforeAll(async () => {
  await setupOnce();
  baseUrl = getBaseUrl();
  buyerToken = await mintToken("buyer", SEED.buyerId);
  driverToken = await mintToken("driver", SEED.driverId);
});

afterAll(async () => {
  await teardownOnce();
});

beforeEach(async () => {
  await query(`TRUNCATE order_items, orders, audit_logs, idempotency_keys RESTART IDENTITY CASCADE`);
  await query(`UPDATE orders SET assigned_driver_id = NULL`);
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

describe("POST /api/v1/auth/login", () => {
  it("logs in a seeded buyer with phone + password and returns a usable JWT", async () => {
    const r = await postJson<{ token: string; userId: string; role: string; trustTier: string }>(
      "/api/v1/auth/login",
      { phone: "+256700000001", password: "demo1234" },
    );
    expect(r.status).toBe(200);
    expect(r.body.userId).toBe(SEED.buyerId);
    expect(r.body.role).toBe("buyer");
    expect(typeof r.body.token).toBe("string");
    expect(r.body.token.length).toBeGreaterThan(20);

    // The issued token must be accepted by an authenticated endpoint.
    const products = await getJson("/api/v1/products", {
      authorization: `Bearer ${r.body.token}`,
    });
    expect(products.status).toBe(200);
  });

  it("logs in a seeded driver", async () => {
    const r = await postJson<{ role: string }>("/api/v1/auth/login", {
      phone: "+256700000003",
      password: "demo1234",
    });
    expect(r.status).toBe(200);
    expect(r.body.role).toBe("driver");
  });

  it("rejects a wrong password with 401 (no user enumeration)", async () => {
    const r = await postJson("/api/v1/auth/login", {
      phone: "+256700000001",
      password: "wrong-password",
    });
    expect(r.status).toBe(401);
  });

  it("rejects an unknown phone with 401", async () => {
    const r = await postJson("/api/v1/auth/login", {
      phone: "+256700009999",
      password: "demo1234",
    });
    expect(r.status).toBe(401);
  });

  it("rejects a missing phone/password with 400", async () => {
    const r = await postJson("/api/v1/auth/login", { phone: "+256700000001" });
    expect(r.status).toBe(400);
  });
});

describe("GET /api/v1/products", () => {
  it("returns the seeded catalog in camelCase to an authenticated buyer", async () => {
    const r = await getJson<Array<{
      id: string;
      sellerId: string;
      title: string;
      priceMinor: number;
      currency: string;
      stockQuantity: number;
      productTrustScore: number;
      imageUrl: string | null;
    }>>("/api/v1/products", { authorization: `Bearer ${buyerToken}` });

    expect(r.status).toBe(200);
    expect(r.body.length).toBeGreaterThanOrEqual(3);
    const tote = r.body.find((p) => p.title === "Bark cloth tote bag");
    expect(tote).toBeDefined();
    expect(tote!.sellerId).toBe(SEED.sellerId);
    expect(tote!.priceMinor).toBe(2_500_000);
    expect(tote!.currency).toBe("UGX");
    expect(tote!.stockQuantity).toBe(25);
  });

  it("requires authentication", async () => {
    const r = await getJson("/api/v1/products");
    expect(r.status).toBe(401);
  });
});

describe("GET /api/v1/logistics/assigned", () => {
  it("returns the driver's assigned orders with line items", async () => {
    // Create an order as the buyer, then assign it to the demo driver.
    const co = await postJson<{ orderId: string }>(
      "/api/v1/orders/checkout",
      { items: [{ productId: SEED.productA, qty: 1 }], deliveryAddress: { line1: "Plot 1", city: "Kampala", country: "UG" } },
      { authorization: `Bearer ${buyerToken}`, "idempotency-key": randomIdempotencyKey() },
    );
    expect(co.status).toBe(201);
    await query(`UPDATE orders SET assigned_driver_id = $1, status = 'assigned' WHERE id = $2`, [
      SEED.driverId,
      co.body.orderId,
    ]);

    const r = await getJson<Array<{
      orderId: string;
      status: string;
      totalMinor: number;
      currency: string;
      items: Array<{ productId: string; qty: number; unitPriceMinor: number }>;
    }>>("/api/v1/logistics/assigned", { authorization: `Bearer ${driverToken}` });

    expect(r.status).toBe(200);
    expect(r.body).toHaveLength(1);
    expect(r.body[0]!.orderId).toBe(co.body.orderId);
    expect(r.body[0]!.status).toBe("assigned");
    expect(r.body[0]!.totalMinor).toBe(2_500_000);
    expect(r.body[0]!.currency).toBe("UGX");
    expect(r.body[0]!.items).toEqual([
      { productId: SEED.productA, qty: 1, unitPriceMinor: 2_500_000 },
    ]);
  });

  it("returns an empty list for a driver with no assignments", async () => {
    const r = await getJson("/api/v1/logistics/assigned", { authorization: `Bearer ${driverToken}` });
    expect(r.status).toBe(200);
    expect(r.body).toEqual([]);
  });

  it("rejects a buyer with 403", async () => {
    const r = await getJson("/api/v1/logistics/assigned", { authorization: `Bearer ${buyerToken}` });
    expect(r.status).toBe(403);
  });
});
