/**
 * GET /api/v1/products — the buyer-facing product catalog.
 *
 * Returns active products (most recent first) in the same camelCase shape the
 * Android client's ProductDto expects. The products_select RLS policy in
 * 0002_rls.sql already allows any authenticated caller to read products, so
 * this route simply runs inside the caller's transaction context.
 */

import type { FastifyInstance } from "fastify";
import { requireAuth, getAuthUser } from "../../auth.js";
import { withTransaction } from "../../db.js";

type ProductRow = {
  id: string;
  seller_id: string;
  title: string;
  description: string;
  price_minor: string;
  currency: string;
  stock_quantity: number;
  product_trust_score: string | null;
  image_url: string | null;
  is_active: boolean;
};

function rowToProduct(r: ProductRow) {
  return {
    id: r.id,
    sellerId: r.seller_id,
    title: r.title,
    description: r.description,
    priceMinor: Number(r.price_minor),
    currency: r.currency,
    stockQuantity: r.stock_quantity,
    productTrustScore: Number(r.product_trust_score ?? "50"),
    imageUrl: r.image_url,
    isActive: r.is_active,
  };
}

export async function registerProductsRoute(app: FastifyInstance): Promise<void> {
  app.get("/api/v1/products", { preHandler: requireAuth }, async (request) => {
    const user = getAuthUser(request);
    return withTransaction({ userId: user.id, role: user.role }, async (c) => {
      const r = await c.query<ProductRow>(
        `SELECT p.id,
                p.seller_id,
                p.title,
                p.description,
                p.price_minor::text,
                p.currency,
                p.stock_quantity,
                COALESCE(p.product_trust_score, 50)::text AS product_trust_score,
                p.is_active,
                (SELECT m.url FROM product_media m
                  WHERE m.product_id = p.id ORDER BY m.position LIMIT 1) AS image_url
           FROM products p
          WHERE p.is_active = true
          ORDER BY p.created_at DESC
          LIMIT 100`,
      );
      return r.rows.map(rowToProduct);
    });
  });
}
