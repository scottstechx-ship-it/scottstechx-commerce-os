/**
 * Nylon Pay adapter — wraps the official `@nile-squad/nylonpay-ts` SDK.
 *
 * Config (env):
 *   NYLONPAY_API_KEY          — the `npk_...` public key
 *   NYLONPAY_API_SECRET       — the `nps_...` secret key
 *   NYLONPAY_BASE_URL         — optional; defaults to the SDK's production base
 *   NYLONPAY_WEBHOOK_SECRET   — signing secret for inbound webhooks (set this
 *                               in the Nylon Pay dashboard when you register
 *                               the webhook URL)
 *
 * The SDK's `reference` field is a UUID and is idempotent: retrying with the
 * same reference returns the existing transaction instead of double-charging.
 */

import { randomUUID } from "node:crypto";
import {
  createNylonPay,
  verifyWebhookSignature,
  type CollectPaymentInput,
  type Currency,
  type NylonPaySdk,
} from "@nile-squad/nylonpay-ts";

export class PaymentProviderError extends Error {
  readonly code = "payment_provider_error";
  constructor(message: string) {
    super(message);
    this.name = "PaymentProviderError";
  }
}

let _sdk: NylonPaySdk | null = null;

export function isNylonPayConfigured(): boolean {
  return !!(process.env.NYLONPAY_API_KEY && process.env.NYLONPAY_API_SECRET);
}

function getSdk(): NylonPaySdk {
  if (_sdk) return _sdk;
  const apiKey = process.env.NYLONPAY_API_KEY;
  const apiSecret = process.env.NYLONPAY_API_SECRET;
  if (!apiKey || !apiSecret) {
    throw new PaymentProviderError("Nylon Pay is not configured (set NYLONPAY_API_KEY and NYLONPAY_API_SECRET)");
  }
  _sdk = createNylonPay({
    apiKey,
    apiSecret,
    baseUrl: process.env.NYLONPAY_BASE_URL || undefined,
  });
  return _sdk;
}

export type CollectParams = {
  amountMinor: number;
  currency: string;
  customerName: string;
  phoneNumber: string;
  description: string;
  reference?: string;
  metadata?: Record<string, string>;
};

export type CollectOutcome = {
  reference: string;
  status: string;
};

/**
 * Fire-and-forget collection: initiates the payment and returns immediately
 * with the transaction reference. Fulfillment is driven by the webhook
 * (`/api/v1/payments/webhook`), never by blocking the HTTP request.
 */
export async function initiateNylonCollection(params: CollectParams): Promise<CollectOutcome> {
  const sdk = getSdk();
  const reference = params.reference ?? randomUUID();
  const input: CollectPaymentInput = {
    amount: params.amountMinor,
    currency: params.currency as Currency,
    customer: {
      name: params.customerName,
      phoneNumber: params.phoneNumber,
    },
    description: params.description,
    reference,
    method: "mobileMoney",
    ...(params.metadata ? { metadata: params.metadata } : {}),
  };
  try {
    const payment = await sdk.collectPayment(input);
    return { reference, status: payment.status };
  } catch (err) {
    throw new PaymentProviderError(
      `Nylon Pay collection failed: ${(err as Error).message}`,
    );
  }
}

/**
 * Verify an inbound webhook's HMAC signature (header `x-nylon-signature`).
 * Runs the SDK's replay/freshness checks. Returns false when the webhook
 * secret is not configured so callers never trust an unverified event.
 */
export function verifyNylonSignature(payload: string | Uint8Array, signature: string): boolean {
  const secret = process.env.NYLONPAY_WEBHOOK_SECRET;
  if (!secret) return false;
  try {
    return verifyWebhookSignature({ payload, signature, secret });
  } catch {
    return false;
  }
}
