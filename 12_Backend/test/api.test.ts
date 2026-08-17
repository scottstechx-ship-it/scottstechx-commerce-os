/**
 * End-to-end tests for POST /api/v1/orders/checkout and POST /api/v1/logistics/pod.
 *
 * These run against a real embedded Postgres (see test/setup.ts) so the RLS
 * policies and triggers are actually exercised — no mocks.
 */

import { describe, it, expect, beforeEach, afterAll, beforeAll } from "vitest";
import {
  setup as setupOnce,
  teardown as teardownOnce,
  getBaseUrl,
  SEED,
  mintToken,
  randomIdempotencyKey,
} from "./setup.js";
import { query, withTransaction } from "../src/db.js";

let baseUrl = "";
let buyerToken = "";
let driverToken = "";
let _sellerToken = "";

beforeAll(async () => {
  await setupOnce();
  baseUrl = getBaseUrl();
  buyerToken = await mintToken("buyer", SEED.buyerId);
  driverToken = await mintToken("driver", SEED.driverId);
  _sellerToken = await mintToken("seller", SEED.sellerId);
});

afterAll(async () => {
  await teardownOnce();
});

// Wipe orders/order_items/audit_logs/idempotency between tests so each test
// is hermetic. We do NOT wipe users/products/seeded data.
async function resetDomainTables() {
  await query(
    `TRUNCATE order_items, orders, audit_logs, idempotency_keys RESTART IDENTITY CASCADE`,
  );
  // Restore stock to seeded levels so each test sees the same catalog.
  await query(`UPDATE products SET stock_quantity = 25 WHERE id = $1`, [SEED.productA]);
  await query(`UPDATE products SET stock_quantity = 12 WHERE id = $1`, [SEED.productB]);
  await query(`UPDATE products SET stock_quantity = 60 WHERE id = $1`, [SEED.productC]);
  // Reset any existing assignments.
  await query(`UPDATE orders SET assigned_driver_id = NULL`);
}

beforeEach(async () => {
  await resetDomainTables();
});

async function assignOrderToDriver(orderId: string) {
  await query(`UPDATE orders SET assigned_driver_id = $1, status = 'assigned' WHERE id = $2`, [
    SEED.driverId,
    orderId,
  ]);
}

async function postJson<T = unknown>(
  path: string,
  token: string,
  idemKey: string,
  body: unknown,
): Promise<{ status: number; body: T }> {
  const res = await fetch(`${baseUrl}${path}`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${token}`,
      "idempotency-key": idemKey,
    },
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

const checkoutBody = {
  items: [{ productId: SEED.productA, qty: 2 }],
  deliveryAddress: { line1: "Plot 14, Kampala Road", city: "Kampala", country: "UG" as const },
};

describe("POST /api/v1/orders/checkout", () => {
  it("creates an order with server-computed total, returns 201, persists items", async () => {
    const idem = randomIdempotencyKey();
    const r = await postJson<{
      orderId: string;
      status: string;
      totalMinor: number;
      currency: string;
      fxRateSnapshot: string;
      items: {
        productId: string;
        qty: number;
        unitPriceMinor: number;
        line_totalMinor: number;
      }[];
    }>("/api/v1/orders/checkout", buyerToken, idem, checkoutBody);

    expect(r.status).toBe(201);
    expect(r.body.status).toBe("created");
    expect(r.body.currency).toBe("UGX");
    expect(r.body.totalMinor).toBe(2 * 2_500_000); // 2 × UGX 2,500,000 minor units
    expect(r.body.items).toHaveLength(1);
    expect(r.body.items[0]!.lineTotalMinor).toBe(5_000_000);
    expect(r.body.fxRateSnapshot).toBe("1.00000000");
    expect(r.body.orderId).toMatch(/^[0-9a-f-]{36}$/);

    // Verify it actually landed in the DB.
    const r1 = await query<{ count: string }>(
      `SELECT count(*)::text AS count FROM order_items WHERE order_id = $1`,
      [r.body.orderId],
    );
    expect(r1.rows[0]!.count).toBe("1");
  });

  it("replays the same Idempotency-Key with byte-identical body", async () => {
    const idem = randomIdempotencyKey();
    const a = await postJson("/api/v1/orders/checkout", buyerToken, idem, checkoutBody);
    const b = await postJson("/api/v1/orders/checkout", buyerToken, idem, checkoutBody);
    expect(a.status).toBe(201);
    // Per the OpenAPI contract, idempotent replays return 200 (not 201).
    expect(b.status).toBe(200);
    expect(b.body).toEqual(a.body);

    // And only one order exists.
    const r = await query<{ count: string }>(
      `SELECT count(*)::text AS count FROM orders WHERE customer_id = $1`,
      [SEED.buyerId],
    );
    expect(r.rows[0]!.count).toBe("1");
  });

  it("replaying the same Idempotency-Key with a different body returns 409", async () => {
    const idem = randomIdempotencyKey();
    const a = await postJson("/api/v1/orders/checkout", buyerToken, idem, checkoutBody);
    expect(a.status).toBe(201);

    const otherBody = { ...checkoutBody, items: [{ productId: SEED.productB, qty: 1 }] };
    const b = await postJson("/api/v1/orders/checkout", buyerToken, idem, otherBody);
    expect(b.status).toBe(409);
  });

  it("rejects a non-buyer caller with 403", async () => {
    const r = await postJson(
      "/api/v1/orders/checkout",
      driverToken,
      randomIdempotencyKey(),
      checkoutBody,
    );
    expect(r.status).toBe(403);
  });

  it("rejects a missing JWT with 401", async () => {
    const res = await fetch(`${baseUrl}/api/v1/orders/checkout`, {
      method: "POST",
      headers: { "content-type": "application/json", "idempotency-key": randomIdempotencyKey() },
      body: JSON.stringify(checkoutBody),
    });
    expect(res.status).toBe(401);
  });
});

describe("POST /api/v1/logistics/pod", () => {
  it("rejects a POD from a driver who is not the assigned driver (403)", async () => {
    // Buyer's checkout creates the order.
    const co = await postJson<{ orderId: string }>(
      "/api/v1/orders/checkout",
      buyerToken,
      randomIdempotencyKey(),
      checkoutBody,
    );
    expect(co.status).toBe(201);

    // Assign the demo driver.
    await assignOrderToDriver(co.body.orderId);

    // Make a second driver (a real, valid UUID; this one has a driver_profile).
    await query(
      `INSERT INTO users (id, email, display_name, role) VALUES ($1, $2, 'Other', 'driver')
       ON CONFLICT (email) DO NOTHING`,
      ["44444444-4444-4444-8444-444444444444", "other-driver@test"],
    );
    await query(
      `INSERT INTO driver_profiles (user_id, vehicle_plate) VALUES ($1, 'OTHER 001')
       ON CONFLICT (user_id) DO NOTHING`,
      ["44444444-4444-4444-8444-444444444444"],
    );
    const otherDriverToken = await mintToken("driver", "44444444-4444-4444-8444-444444444444");

    const r = await postJson("/api/v1/logistics/pod", otherDriverToken, randomIdempotencyKey(), {
      orderId: co.body.orderId,
      action: "pickup",
      gpsLat: 0.3476,
      gpsLng: 32.5825,
    });
    expect(r.status).toBe(403);
  });

  it("rejects a POD for an order not in {assigned} with 409", async () => {
    const co = await postJson<{ orderId: string }>(
      "/api/v1/orders/checkout",
      buyerToken,
      randomIdempotencyKey(),
      checkoutBody,
    );
    expect(co.status).toBe(201);

    // Order is in 'created', not 'assigned' — pickup must fail.
    // Assign the demo driver first so the 403 check passes.
    await assignOrderToDriver(co.body.orderId);

    const r = await postJson("/api/v1/logistics/pod", driverToken, randomIdempotencyKey(), {
      orderId: co.body.orderId,
      action: "pickup",
      gpsLat: 0.3476,
      gpsLng: 32.5825,
    });
    expect(r.status).toBe(200);
    // Now the order is in picked_up. Try pickup again — should 409.
    const r2 = await postJson("/api/v1/logistics/pod", driverToken, randomIdempotencyKey(), {
      orderId: co.body.orderId,
      action: "pickup",
      gpsLat: 0.3476,
      gpsLng: 32.5825,
    });
    expect(r2.status).toBe(409);
  });

  it("happy path: assigned -> picked_up -> delivered", async () => {
    const co = await postJson<{ orderId: string }>(
      "/api/v1/orders/checkout",
      buyerToken,
      randomIdempotencyKey(),
      checkoutBody,
    );
    expect(co.status).toBe(201);

    await assignOrderToDriver(co.body.orderId);

    const pickup = await postJson<{ orderId: string; status: string }>(
      "/api/v1/logistics/pod",
      driverToken,
      randomIdempotencyKey(),
      { orderId: co.body.orderId, action: "pickup", gpsLat: 0.3476, gpsLng: 32.5825 },
    );
    expect(pickup.status).toBe(200);
    expect(pickup.body.status).toBe("picked_up");

    const deliver = await postJson<{ orderId: string; status: string }>(
      "/api/v1/logistics/pod",
      driverToken,
      randomIdempotencyKey(),
      { orderId: co.body.orderId, action: "deliver", gpsLat: 0.3476, gpsLng: 32.5825 },
    );
    expect(deliver.status).toBe(200);
    expect(deliver.body.status).toBe("delivered");
  });
});

describe("RLS cross-tenant denial", () => {
  it("an order belonging to another customer is invisible to the wrong app.user_id", async () => {
    const co = await postJson<{ orderId: string }>(
      "/api/v1/orders/checkout",
      buyerToken,
      randomIdempotencyKey(),
      checkoutBody,
    );
    expect(co.status).toBe(201);

    // A different buyer (not the owner) sees zero rows when reading orders.
    // The default postgres superuser has BYPASSRLS as a role attribute, which
    // cannot be revoked per-transaction. To verify the policy actually denies
    // cross-tenant reads, we use a non-superuser role created on the fly and
    // grant it the right to read orders. This is the production-realistic
    // scenario: the app connects as `app`, not as postgres.
    const r = await withTransaction(
      { userId: "99999999-9999-4999-8999-999999999999" },
      async (client) => {
        // Create a non-superuser role that does NOT have BYPASSRLS, grant it
        // access to the orders table, and SET LOCAL ROLE to it.
        await client.query(`
          DO $$ BEGIN
            IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rls_tester') THEN
              CREATE ROLE rls_tester NOLOGIN;
            END IF;
          END $$
        `);
        await client.query(`GRANT USAGE ON SCHEMA public TO rls_tester`);
        await client.query(
          `GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO rls_tester`,
        );
        // Ensure the role has no BYPASSRLS attribute (default for new roles).
        await client.query(`ALTER ROLE rls_tester NOBYPASSRLS`);
        await client.query(`SET LOCAL ROLE rls_tester`);
        const x = await client.query<{ count: string }>(
          `SELECT count(*)::text AS count FROM orders WHERE id = $1`,
          [co.body.orderId],
        );
        return x.rows[0]!.count;
      },
    );
    expect(r).toBe("0");

    // The owner still sees it.
    const r2 = await withTransaction({ userId: SEED.buyerId }, async (client) => {
      await client.query(`SET LOCAL ROLE rls_tester`);
      const x = await client.query<{ count: string }>(
        `SELECT count(*)::text AS count FROM orders WHERE id = $1`,
        [co.body.orderId],
      );
      return x.rows[0]!.count;
    });
    expect(r2).toBe("1");
  });
});

describe("audit log immutability", () => {
  it("app role cannot UPDATE or DELETE from audit_logs", async () => {
    await postJson("/api/v1/orders/checkout", buyerToken, randomIdempotencyKey(), checkoutBody);
    // Try to update — must throw.
    const updateAttempt = await withTransaction({ userId: SEED.buyerId }, async (client) => {
      try {
        await client.query(`UPDATE audit_logs SET action = 'tampered' WHERE id = 1`);
        return "ok";
      } catch (e) {
        return (e as Error).message;
      }
    });
    expect(updateAttempt).toMatch(/append-only/);

    const deleteAttempt = await withTransaction({ userId: SEED.buyerId }, async (client) => {
      try {
        await client.query(`DELETE FROM audit_logs WHERE id = 1`);
        return "ok";
      } catch (e) {
        return (e as Error).message;
      }
    });
    expect(deleteAttempt).toMatch(/append-only/);
  });
});
