/**
 * POD (Proof of Delivery) service.
 *
 * Properties enforced:
 *   1. Driver identity from JWT, not request body.
 *   2. The order's assigned_driver_id MUST equal the caller. RLS plus an
 *      explicit service-level guard both enforce this.
 *   3. State machine: pickup only legal from {assigned}, deliver only from
 *      {picked_up}. Illegal transitions throw 409.
 *   4. POD updates orders.status and writes the GPS + timestamp into the
 *      pod_* columns, all in one transaction.
 *   5. Audit log: append-only.
 */

import type { PoolClient } from "pg";
import { withTransaction } from "../../db.js";
import {
  BadRequestError,
  ConflictError,
  ForbiddenError,
  NotFoundError,
  UnauthorizedError,
  UnprocessableError,
} from "../../errors.js";
import type { AuthUser } from "../../auth.js";
import { hashRequest, checkIdempotency, storeIdempotency } from "../orders/idempotency.js";
import { POD_ACTION_STATUS } from "../orders/order.state.js";
import { insertAuditLog } from "../audit/audit.js";
import type { PodBody, PodResponse } from "./pod.schema.js";

export async function submitPod(
  user: AuthUser,
  idempotencyKey: string,
  body: PodBody,
): Promise<{ status: number; body: PodResponse }> {
  if (user.role !== "driver") {
    throw new ForbiddenError("only drivers can submit POD");
  }
  if (!idempotencyKey) {
    throw new BadRequestError("missing Idempotency-Key header");
  }
  const requestHash = hashRequest(body);

  return withTransaction({ userId: user.id, role: user.role }, async (client) => {
    const replay = await checkIdempotency(client, user.id, idempotencyKey, requestHash);
    if (replay) {
      return {
        status: replay.response_status,
        body: replay.response_body as PodResponse,
      };
    }

    const result = await applyPodTx(client, user, body);

    await storeIdempotency(client, user.id, idempotencyKey, requestHash, 200, result);

    return { status: 200, body: result };
  });
}

async function applyPodTx(client: PoolClient, user: AuthUser, body: PodBody): Promise<PodResponse> {
  // Confirm a driver_profile exists for this user. RLS allows driver to see
  // own profile, and an anonymous join would 0-row. We pin by user_id
  // (the PK on driver_profiles).
  const driverResult = await client.query<{ user_id: string }>(
    `SELECT user_id FROM driver_profiles WHERE user_id = $1`,
    [user.id],
  );
  if (driverResult.rowCount === 0) {
    throw new UnauthorizedError("no driver profile for caller");
  }
  const driverId = driverResult.rows[0]!.user_id;

  // Lock the order row to avoid two drivers racing the same order.
  const orderResult = await client.query<{
    id: string;
    status: string;
    assigned_driver_id: string | null;
  }>(
    `SELECT id, status, assigned_driver_id
       FROM orders
      WHERE id = $1
      FOR UPDATE`,
    [body.orderId],
  );
  if (orderResult.rowCount === 0) {
    throw new NotFoundError(`order ${body.orderId} not found`);
  }
  const order = orderResult.rows[0]!;
  if (order.assigned_driver_id !== driverId) {
    throw new ForbiddenError("order is not assigned to this driver");
  }

  const transition = POD_ACTION_STATUS[body.action];
  if (order.status !== transition.from) {
    throw new ConflictError(
      `cannot ${body.action} order in status ${order.status}; expected ${transition.from}`,
      { orderId: order.id, currentStatus: order.status, requiredStatus: transition.from },
    );
  }

  // Update the order with the new status, GPS, signature, and timestamp.
  if (body.action === "pickup") {
    await client.query(
      `UPDATE orders
          SET status = $1, pod_pickup_at = now(), pod_pickup_lat = $2, pod_pickup_lng = $3,
              pod_signature = COALESCE($4, pod_signature)
        WHERE id = $5`,
      [transition.to, body.gpsLat, body.gpsLng, body.signaturePngBase64 ?? null, order.id],
    );
  } else {
    await client.query(
      `UPDATE orders
          SET status = $1, pod_delivered_at = now(), pod_delivered_lat = $2, pod_delivered_lng = $3,
              pod_signature = COALESCE($4, pod_signature)
        WHERE id = $5`,
      [transition.to, body.gpsLat, body.gpsLng, body.signaturePngBase64 ?? null, order.id],
    );
  }

  await insertAuditLog(client, {
    actor_user_id: user.id,
    action: `pod.${body.action}`,
    resource_type: "order",
    resource_id: order.id,
    payload: {
      gpsLat: body.gpsLat,
      gpsLng: body.gpsLng,
      notes: body.notes ?? null,
    },
  });

  // Unreachable in practice but keeps the linter honest.
  void (UnprocessableError as unknown);
  return {
    orderId: order.id,
    status: transition.to as PodResponse["status"],
  };
}
