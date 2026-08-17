/**
 * Payment routes:
 *   POST /api/v1/payments/collect  (buyer)  — start a collection for an order
 *   POST /api/v1/payments/webhook  (public) — Nylon Pay signed callback
 *
 * The webhook route enables `rawBody` (via fastify-raw-body) so the HMAC
 * signature is verified against the exact bytes Nylon Pay signed, not a
 * re-serialized copy.
 */

import { z } from "zod";
import type { FastifyInstance } from "fastify";
import { requireAuth, getAuthUser } from "../../auth.js";
import { BadRequestError } from "../../errors.js";
import { collectPayment, settleWebhook, isWebhookSignatureValid } from "./payments.service.js";

const collectSchema = z.object({
  orderId: z.string().uuid(),
});

export async function registerPaymentsRoute(app: FastifyInstance): Promise<void> {
  app.post(
    "/api/v1/payments/collect",
    { preHandler: requireAuth },
    async (request, reply) => {
      const user = getAuthUser(request);
      if (user.role !== "buyer") {
        reply.status(403).send({ error: "forbidden", message: "buyers only" });
        return;
      }
      const body = collectSchema.parse(request.body);
      const result = await collectPayment(user, body.orderId);
      reply.status(200).send(result);
    },
  );

  app.post(
    "/api/v1/payments/webhook",
    { config: { rawBody: true } },
    async (request, reply) => {
      const signature = (request.headers["x-nylon-signature"] as string | undefined) ?? "";
      const rawBody = request.rawBody ?? "";

      if (!isWebhookSignatureValid(rawBody, signature)) {
        reply.status(401).send({ error: "unauthorized", message: "invalid webhook signature" });
        return;
      }

      const parsed = (request.body ?? {}) as {
        event?: string;
        payload?: { reference?: string; status?: string };
      };
      const reference = parsed.payload?.reference;
      const status = parsed.payload?.status;
      if (typeof reference !== "string" || reference.length < 8 || typeof status !== "string") {
        throw new BadRequestError("webhook body missing payload.reference/status");
      }

      const result = await settleWebhook({ reference, status });
      reply.status(200).send({ ok: true, ...result });
    },
  );
}
