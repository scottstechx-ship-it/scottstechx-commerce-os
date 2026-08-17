/**
 * Lightweight catalog grounding for the customer-chat assistant.
 *
 * The LLM is a text model — it has no database access. Before we call it, we
 * search the product catalog for items matching the customer's message and
 * hand the matches to the model as context, so answers are grounded in the
 * real catalog instead of hallucinated.
 */

import { withTransaction } from "../../db.js";
import type { AuthUser } from "../../auth.js";

const STOPWORDS = new Set([
  "the", "a", "an", "and", "or", "for", "with", "from", "have", "has", "you",
  "your", "i", "me", "my", "we", "our", "is", "are", "was", "were", "be",
  "please", "can", "could", "would", "do", "does", "did", "want", "need",
  "show", "find", "get", "buy", "any", "some", "about", "what", "which",
  "how", "much", "many", "available", "there", "price", "cost", "looking",
]);

function keywords(message: string): string[] {
  const words = message
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, " ")
    .split(/\s+/)
    .filter((w) => w.length > 2 && !STOPWORDS.has(w));
  return [...new Set(words)].slice(0, 4);
}

export type CatalogHit = {
  title: string;
  priceMinor: number;
  currency: string;
  stockQuantity: number;
};

export function formatCatalogHits(hits: CatalogHit[]): string {
  if (hits.length === 0) return "(no matching products in the catalog)";
  return hits
    .map(
      (h) =>
        `- ${h.title} — ${h.priceMinor.toLocaleString("en-US")} ${h.currency} (${h.stockQuantity} in stock)`,
    )
    .join("\n");
}

export async function searchCatalogForQuery(user: AuthUser, message: string): Promise<CatalogHit[]> {
  const terms = keywords(message);
  if (terms.length === 0) return [];

  const clauses = terms.map((_, i) => `(p.title ILIKE $${i + 1} OR p.description ILIKE $${i + 1})`).join(" OR ");
  const params = terms.map((t) => `%${t}%`);

  return withTransaction({ userId: user.id, role: user.role }, async (c) => {
    const r = await c.query<{
      title: string;
      price_minor: string;
      currency: string;
      stock_quantity: number;
    }>(
      `SELECT p.title, p.price_minor::text, p.currency, p.stock_quantity
         FROM products p
        WHERE p.is_active = true AND (${clauses})
        ORDER BY p.product_trust_score DESC NULLS LAST
        LIMIT 5`,
      params,
    );
    return r.rows.map((row) => ({
      title: row.title,
      priceMinor: Number(row.price_minor),
      currency: row.currency,
      stockQuantity: row.stock_quantity,
    }));
  });
}
