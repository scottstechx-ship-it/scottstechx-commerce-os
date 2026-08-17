/**
 * Zod schema for POST /api/v1/orders/checkout
 *
 * NOTE: No prices, no totals, no driver_id. All of those are server-derived.
 * Field names are camelCase to match the Android client and the rest of the
 * marketplace endpoints.
 */

import { z } from "zod";

export const checkoutBodySchema = z.object({
  items: z
    .array(
      z.object({
        productId: z.string().uuid(),
        qty: z.number().int().min(1).max(999),
      }),
    )
    .min(1)
    .max(50),
  deliveryAddress: z.object({
    line1: z.string().min(1).max(200),
    city: z.string().min(1).max(100),
    country: z.literal("UG"),
  }),
});

export type CheckoutBody = z.infer<typeof checkoutBodySchema>;

export const checkoutResponseSchema = z.object({
  orderId: z.string().uuid(),
  status: z.enum([
    "created",
    "paid",
    "assigned",
    "picked_up",
    "delivered",
    "cancelled",
    "refunded",
  ]),
  totalMinor: z.number().int().nonnegative(),
  currency: z.string().length(3),
  fxRateSnapshot: z.string(),
  items: z.array(
    z.object({
      productId: z.string().uuid(),
      qty: z.number().int(),
      unitPriceMinor: z.number().int().nonnegative(),
      lineTotalMinor: z.number().int().nonnegative(),
    }),
  ),
});

export type CheckoutResponse = z.infer<typeof checkoutResponseSchema>;
