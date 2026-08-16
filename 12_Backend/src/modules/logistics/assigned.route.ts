/**
 * GET /api/v1/logistics/assigned — orders assigned to the calling driver.
 *
 * Returns orders where assigned_driver_id = caller and status is in
 * {assigned, picked_up} (i.e. actionable), mapped to the Android client's
 * OrderResponse shape. The orders_select RLS policy already scopes rows to
 * the assigned driver; the route additionally pins the driver role.
 */

import type { FastifyInstance } from "fastify";
import { requireAuth, getAuthUser } from "../../auth.js";
import { withTransaction } from "../../db.js";
import { ForbiddenError } from "../../errors.js";

type OrderRow = {
  id: string;
  status: string;
  total_minor: string;
  currency: string;
};

type ItemRow = {
  order_id: string;
  product_id: string;
  qty: number;
  unit_price_minor: string;
};

export async function registerAssignedOrdersRoute(app: FastifyInstance): Promise<void> {
  app.get("/api/v1/logistics/assigned", { preHandler: requireAuth }, async (request) => {
    const user = getAuthUser(request);
    if (user.role !== "driver") {
      throw new ForbiddenError("only drivers can list assigned orders");
    }

    return withTransaction({ userId: user.id, role: user.role }, async (c) => {
      const orders = await c.query<OrderRow>(
        `SELECT id, status, total_minor::text, currency
           FROM orders
          WHERE assigned_driver_id = $1
            AND status IN ('assigned', 'picked_up')
          ORDER BY created_at DESC`,
        [user.id],
      );
      if (orders.rowCount === 0) return [];

      const items = await c.query<ItemRow>(
        `SELECT order_id, product_id, qty, unit_price_minor::text
           FROM order_items
          WHERE order_id = ANY($1::uuid[])
          ORDER BY created_at`,
        [orders.rows.map((o) => o.id)],
      );

      const itemsByOrder = new Map<string, { productId: string; qty: number; unitPriceMinor: number }[]>();
      for (const it of items.rows) {
        const list = itemsByOrder.get(it.order_id) ?? [];
        list.push({
          productId: it.product_id,
          qty: it.qty,
          unitPriceMinor: Number(it.unit_price_minor),
        });
        itemsByOrder.set(it.order_id, list);
      }

      return orders.rows.map((o) => ({
        orderId: o.id,
        status: o.status,
        totalMinor: Number(o.total_minor),
        currency: o.currency,
        items: itemsByOrder.get(o.id) ?? [],
      }));
    });
  });
}
