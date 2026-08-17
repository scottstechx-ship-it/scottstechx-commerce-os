/**
 * Trust tier lookup — shared by login and Google auth so both return the
 * same tier vocabulary: BRONZE / SILVER / GOLD / PLATINUM.
 *
 * For the MVP the tier is derived from seller_trust_score; buyers and
 * drivers without a seller profile get BRONZE.
 */

import { withTransaction } from "../../db.js";

export async function computeTrustTier(userId: string): Promise<string> {
  return withTransaction({ userId: null, role: null }, async (c) => {
    const r = await c.query<{ seller_trust_score: string | null }>(
      `SELECT seller_trust_score FROM seller_profiles WHERE user_id = $1`,
      [userId],
    );
    const score = Number(r.rows[0]?.seller_trust_score ?? 0);
    if (score >= 85) return "PLATINUM";
    if (score >= 70) return "GOLD";
    if (score >= 50) return "SILVER";
    return "BRONZE";
  });
}
