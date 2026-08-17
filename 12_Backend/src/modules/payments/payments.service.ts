/**
 * Payment service — starts a collection and advances the order through
 * created -> paid -> assigned once the provider confirms.
 *
 * Provider selection is `PAYMENT_PROVIDER`:
 *   - `nylonpay` (default when NYLONPAY_API_KEY is set) — real collection
 *     via the official SDK; fulfillment comes from the signed webhook.
 *   - `demo` — auto-approves synchronously so the full flow works locally
 *     and in tests without real credentials. Fail-closed: never selected
 *     automatically; only when PAYMENT_PROVIDER=demo is set explicitly.
 *
 * RLS note (dev/tests): the embedded-postgres connection is the cluster
 * superuser, so RLS is bypassed — the same reality as the rest of the app.
 * In production the webhook handler runs with role 'admin' so the
 * `payments_select`/`payments_update` policies and the order policies admit
 * it; the buyer-scoped checks below are still enforced in the SQL.
 */

import type { PoolClient } from "pg";
import { randomUUID } from "node:crypto";
import { withTransaction } from "../../db.js";
import {
  BadRequestError,
  ConflictError,
  NotFoundError,
  UnprocessableError,
} from "../../errors.js";
import type { AuthUser } from "../../auth.js";
import { insertAuditLog } from "../audit/audit.js";
import {
  initiateNylonCollection,
  isNylonPayConfigured,
  verifyNylonSignature,
} from "./nylonpay.js";

export type PaymentMode = "nylonpay" | "demo" | "disabled";

export function getPaymentMode(): PaymentMode {
  const explicit = (process.env.PAYMENT_PROVIDER ?? "").toLowerCase();
  if (explicit === "demo") return "demo";
  if (explicit === "nylonpay") return isNylonPayConfigured() ? "nylonpay" : "disabled";
  return isNylonPayConfigured() ? "nylonpay" : "disabled";
}

type OrderForPayment = {
  id: string;
  customer_id: string;
  total_minor: string;
  currency: string;
  status: string;
};

type CustomerRow = { display_name: string; phone: string | null };

export async function collectPayment(
  user: AuthUser,
  orderId: string,
): Promise<{ reference: string; status: string; provider: string }> {
  const mode = getPaymentMode();
  if (mode === "disabled") {
    throw new BadRequestError("payments are disabled (set NYLONPAY_API_KEY or PAYMENT_PROVIDER=demo)");
  }

  // Read the order + customer first (no long-lived lock while the SDK calls out).
  const order = await withTransaction({ userId: user.id, role: user.role }, async (c) => {
    const r = await c.query<OrderForPurchase>(
      `SELECT id, customer_id, total_minor::text, currency, status
         FROM orders WHERE id = $1 AND customer_id = $2`,
      [orderId, user.id],
    );
    const row = r.rows[0];
    if (!row) throw new NotFoundError("order not found");
    const cust = await c.query<CustomerRow>(
      `SELECT display_name, phone FROM users WHERE id = $1`,
      [user.id],
    );
    return { order: row, customer: cust.rows[0] };
  });

  if (order.order.status !== "created") {
    throw new ConflictError(`order ${orderId} is not payable (status: ${order.order.status})`, {
      orderId,
      status: order.order.status,
    });
  }

  const reference = randomUUID();
  const amountMinor = Number(order.order.total_minor);
  const currency = order.order.currency;

  let providerStatus = "processing";
  if (mode === "nylonpay") {
    if (!order.customer?.phone) {
      throw new UnprocessableError("your account has no phone number; add one before paying", {
        orderId,
      });
    }
    const outcome = await initiateNylonCollection({
      amountMinor,
      currency,
      customerName: order.customer.display_name,
      phoneNumber: order.customer.phone,
      description: `ScottsTechX order ${orderId}`,
      reference,
      metadata: { orderId },
    });
    providerStatus = outcome.status;
  }

  // Record the payment; in demo mode also advance the order immediately.
  await withTransaction({ userId: user.id, role: user.role }, async (c) => {
    await c.query(
      `INSERT INTO payments (order_id, provider, reference, amount_minor, currency, status, provider_status)
       VALUES ($1, $2, $3, $4, $5, $6, $7)`,
      [orderId, mode, reference, amountMinor, currency, mode === "demo" ? "successful" : "processing", providerStatus],
    );
    if (mode === "demo") {
      await advanceOrderToAssigned(c, orderId, user.id);
    }
  });

  return { reference, status: mode === "demo" ? "successful" : providerStatus, provider: mode };
}

type OrderForPurchase = OrderForPayment;

/**
 * Advance an order created -> paid -> assigned (picking an available driver).
 * Assumes the caller already holds/established the right context.
 */
async function advanceOrderToAssigned(client: PoolClient, orderId: string, actorUserId: string): Promise<void> {
  const locked = await client.query<{ status: string }>(
    `SELECT status FROM orders WHERE id = $1 FOR UPDATE`,
    [orderId],
  );
  const current = locked.rows[0]?.status;
  if (current !== "created" && current !== "paid") {
    return; // already advanced elsewhere; nothing to do
  }

  if (current === "created") {
    await client.query(`UPDATE orders SET status = 'paid', updated_at = now() WHERE id = $1`, [orderId]);
  }

  const driver = await client.query<{ user_id: string }>(
    `SELECT dp.user_id
       FROM driver_profiles dp
      WHERE dp.is_available = true
      ORDER BY (
        SELECT count(*) FROM orders o
         WHERE o.assigned_driver_id = dp.user_id AND o.status IN ('assigned', 'picked_up')
      ) ASC, dp.user_id ASC
      LIMIT 1`,
  );
  if ((driver.rowCount ?? 0) > 0) {
    await client.query(
      `UPDATE orders SET assigned_driver_id = $1, status = 'assigned', updated_at = now() WHERE id = $2`,
      [driver.rows[0]!.user_id, orderId],
    );
  }

  await insertAuditLog(client, {
    actor_user_id: actorUserId,
    action: "payment.settled",
    resource_type: "order",
    resource_id: orderId,
    payload: { nextStatus: (driver.rowCount ?? 0) > 0 ? "assigned" : "paid" },
  });
}

/**
 * Handle a verified provider webhook: mark the payment terminal and, on
 * `transaction.successful`, advance the order. Runs with role 'admin' so the
 * RLS policies admit the service-level write (dev/tests bypass RLS anyway).
 */
export async function settleWebhook(body: {
  reference: string;
  status: string;
}): Promise<{ settled: boolean }> {
  return withTransaction({ userId: null, role: "admin" }, async (c) => {
    const payment = await c.query<{ order_id: string; status: string }>(
      `SELECT order_id, status FROM payments WHERE reference = $1 FOR UPDATE`,
      [body.reference],
    );
    if ((payment.rowCount ?? 0) === 0) {
      // Unknown reference — acknowledge so the provider stops retrying.
      return { settled: false };
    }
    const row = payment.rows[0]!;

    if (body.status === "successful") {
      if (row.status !== "successful") {
        await c.query(`UPDATE payments SET status = 'successful', provider_status = $2, updated_at = now() WHERE reference = $1`, [
          body.reference,
          body.status,
        ]);
        await advanceOrderToAssigned(c, row.order_id, row.order_id);
      }
    } else if (body.status === "failed" || body.status === "cancelled") {
      await c.query(`UPDATE payments SET status = $2, provider_status = $2, updated_at = now() WHERE reference = $1`, [
        body.reference,
        body.status,
      ]);
    }
    return { settled: true };
  });
}

export function isWebhookSignatureValid(payload: string | Uint8Array, signature: string): boolean {
  return verifyNylonSignature(payload, signature);
}
