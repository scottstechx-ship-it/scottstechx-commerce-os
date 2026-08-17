/**
 * Checkout service — the meat of POST /api/v1/orders/checkout.
 *
 * Properties enforced here:
 *   1. Caller identity from JWT, not from request body.
 *   2. Money is server-computed: server reads each product's current price
 *      and currency, multiplies by qty, snapshots to order_items.
 *   3. Total is in BIGINT minor units, currency in CHAR(3).
 *   4. fx_rate_snapshot is captured at insert time and stored on the order.
 *   5. Idempotency: (user_id, key) uniqueness, hash mismatch -> 409.
 *   6. RLS: all reads run inside withTransaction({ userId }) so current_setting
 *      is populated. Anonymous connections would see zero rows by design.
 *   7. Stock decrement: same transaction, so a crash rolls everything back.
 *   8. Audit log: append-only INSERT into audit_logs, no UPDATE/DELETE.
 */

import type { PoolClient } from "pg";
import { withTransaction } from "../../db.js";
import { BadRequestError, UnprocessableError, ForbiddenError } from "../../errors.js";
import type { AuthUser } from "../../auth.js";
import { hashRequest, checkIdempotency, storeIdempotency } from "./idempotency.js";
import { getRateUGXto, isSupportedCurrency, type Currency } from "../fx/fx.js";
import { insertAuditLog } from "../audit/audit.js";
import type { CheckoutBody, CheckoutResponse } from "./checkout.schema.js";

export async function checkout(
  user: AuthUser,
  idempotencyKey: string,
  body: CheckoutBody,
): Promise<{ status: number; body: CheckoutResponse }> {
  if (user.role !== "buyer") {
    throw new ForbiddenError("only buyers can checkout");
  }
  if (!idempotencyKey) {
    throw new BadRequestError("missing Idempotency-Key header");
  }
  const requestHash = hashRequest(body);

  return withTransaction({ userId: user.id, role: user.role }, async (client) => {
    const replay = await checkIdempotency(client, user.id, idempotencyKey, requestHash);
    if (replay) {
      // Idempotent replay: per the OpenAPI spec, replays return 200 with the
      // original body, distinguishing them from a fresh creation (201).
      return {
        status: 200,
        body: replay.response_body as CheckoutResponse,
      };
    }

    const result = await createOrderTx(client, user, body);

    await storeIdempotency(client, user.id, idempotencyKey, requestHash, 201, result);

    return { status: 201, body: result };
  });
}

async function createOrderTx(
  client: PoolClient,
  user: AuthUser,
  body: CheckoutBody,
): Promise<CheckoutResponse> {
  // 1. Load products (RLS-scoped: buyer sees only active products, by design of
  //    products policies in 0002_rls.sql — for MVP we allow all authenticated
  //    users to SELECT products, so this returns the full catalog).
  const productIds = body.items.map((i) => i.productId);
  const productsResult = await client.query<{
    id: string;
    seller_id: string;
    price_minor: string; // BIGINT comes back as string
    currency: string;
    stock_quantity: number;
  }>(
    `SELECT id, seller_id, price_minor::text, currency, stock_quantity
       FROM products
      WHERE id = ANY($1::uuid[]) AND is_active = true
      FOR UPDATE`,
    [productIds],
  );

  if (productsResult.rowCount !== productIds.length) {
    throw new UnprocessableError("one or more products not found or inactive", {
      requested: productIds,
      found: productsResult.rows.map((r) => r.id),
    });
  }

  // 2. Validate all products share a single seller (multi-seller cart is out
  //    of scope for the MVP). Reject otherwise so the buyer doesn't get a
  //    surprising split.
  const productById = new Map(productsResult.rows.map((p) => [p.id, p]));
  const sellerIds = new Set(productsResult.rows.map((p) => p.seller_id));
  if (sellerIds.size !== 1) {
    throw new UnprocessableError("cart must contain products from a single seller");
  }
  const sellerId = [...sellerIds][0]!;

  // 3. Validate stock, currency support, and FX rates.
  const items: CheckoutResponse["items"] = [];
  let totalMinor = 0n;
  let orderCurrency: Currency | null = null;

  for (const line of body.items) {
    const product = productById.get(line.productId);
    if (!product) {
      // Shouldn't reach here due to the SELECT above, but be defensive.
      throw new UnprocessableError(`unknown product ${line.productId}`);
    }
    if (product.stock_quantity < line.qty) {
      throw new UnprocessableError(`insufficient stock for product ${product.id}`);
    }
    if (!isSupportedCurrency(product.currency)) {
      throw new UnprocessableError(
        `unsupported currency ${product.currency} on product ${product.id}`,
      );
    }
    // All items in a single order must share a currency for the MVP.
    if (orderCurrency === null) {
      orderCurrency = product.currency as Currency;
    } else if (orderCurrency !== product.currency) {
      throw new UnprocessableError("mixed-currency cart is not supported in MVP");
    }

    const unit = BigInt(product.price_minor);
    const lineTotal = unit * BigInt(line.qty);
    totalMinor += lineTotal;

    items.push({
      productId: product.id,
      qty: line.qty,
      unitPriceMinor: Number(unit),
      lineTotalMinor: Number(lineTotal),
    });
  }

  // 4. FX snapshot. For UGX the rate is identity; for other supported
  //    currencies we look up the latest rate.
  const { rate } = await getRateUGXto(client, orderCurrency!);

  // 5. Insert order + items + decrement stock + audit. All in one tx.
  const orderResult = await client.query<{ id: string; created_at: string }>(
    `INSERT INTO orders (customer_id, seller_id, total_minor, currency, fx_rate_snapshot, status, delivery_address)
     VALUES ($1, $2, $3, $4, $5, 'created', $6)
     RETURNING id, created_at`,
    [
      user.id,
      sellerId,
      totalMinor.toString(),
      orderCurrency,
      rate,
      JSON.stringify(body.deliveryAddress),
    ],
  );
  const orderId = orderResult.rows[0]!.id;

  for (const line of body.items) {
    await client.query(
      `INSERT INTO order_items (order_id, product_id, qty, unit_price_minor, currency)
       VALUES ($1, $2, $3, $4, $5)`,
      [
        orderId,
        line.productId,
        line.qty,
        productById.get(line.productId)!.price_minor,
        orderCurrency,
      ],
    );
    await client.query(`UPDATE products SET stock_quantity = stock_quantity - $1 WHERE id = $2`, [
      line.qty,
      line.productId,
    ]);
  }

  await insertAuditLog(client, {
    actor_user_id: user.id,
    action: "order.create",
    resource_type: "order",
    resource_id: orderId,
    payload: { items: items.length, total_minor: totalMinor.toString() },
  });

  return {
    orderId,
    status: "created",
    totalMinor: Number(totalMinor),
    currency: orderCurrency!,
    fxRateSnapshot: rate,
    items,
  };
}
