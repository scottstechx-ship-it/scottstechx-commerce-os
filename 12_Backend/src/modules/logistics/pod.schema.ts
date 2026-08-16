/**
 * Zod schema for POST /api/v1/logistics/pod
 *
 * IMPORTANT: No driver_id. The driver is derived from the JWT.
 * Field names are camelCase to match the Android client.
 */

import { z } from "zod";

export const podBodySchema = z.object({
  orderId: z.string().uuid(),
  action: z.enum(["pickup", "deliver"]),
  gpsLat: z.number().min(-90).max(90),
  gpsLng: z.number().min(-180).max(180),
  notes: z.string().max(500).optional(),
  // Base64-encoded signature photo captured by the driver at delivery.
  signaturePngBase64: z.string().max(2_000_000).optional(),
});

export type PodBody = z.infer<typeof podBodySchema>;

export const podResponseSchema = z.object({
  orderId: z.string().uuid(),
  status: z.enum(["picked_up", "delivered"]),
});

export type PodResponse = z.infer<typeof podResponseSchema>;
