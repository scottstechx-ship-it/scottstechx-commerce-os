/**
 * Fastify server.
 *
 * Boots: migrate -> register routes -> start.
 *
 * Routes registered:
 *   Public:
 *     GET  /healthz
 *     GET  /api/v1/healthz
 *     POST /api/v1/auth/google
 *     POST /api/v1/auth/login
 *     POST /api/v1/auth/firebase
 *     POST /api/v1/auth/register
 *     POST /api/v1/payments/webhook
 *     GET  /api/v1/ai/status
 *   Authenticated (JWT):
 *     GET  /api/v1/products
 *     POST /api/v1/orders/checkout
 *     POST /api/v1/payments/collect
 *     POST /api/v1/logistics/pod
 *     GET  /api/v1/logistics/assigned
 *     GET  /api/v1/sellers/nearby
 *     GET  /api/v1/sellers/:sellerId
 *     GET  /api/v1/seller/profile
 *     PATCH /api/v1/seller/profile
 *     GET  /api/v1/seller/inventory
 *     POST /api/v1/seller/inventory
 *     PATCH /api/v1/seller/inventory/:productId
 *     DELETE /api/v1/seller/inventory/:productId
 *     GET  /api/v1/seller/stats
 *     GET  /api/v1/seller/orders
 *     POST /api/v1/reviews
 *     GET  /api/v1/chat/messages
 *     POST /api/v1/chat/messages
 *     POST /api/v1/ai/seller-suggest  (rate-limited)
 *     POST /api/v1/ai/customer-chat   (rate-limited)
 *     POST /api/v1/ai/reason          (rate-limited)
 */

import { pathToFileURL } from "node:url";
import Fastify, { type FastifyError, type FastifyInstance } from "fastify";
import fastifyRawBody from "fastify-raw-body";
import { loadEnvFile } from "./env.js";
import { AppError, NotImplementedError } from "./errors.js";
import { runMigrations } from "./migrate.js";
import { registerCheckoutRoute } from "./modules/orders/checkout.route.js";
import { registerPodRoute } from "./modules/logistics/pod.route.js";
import { registerNearbyRoute } from "./modules/sellers/nearby.route.js";
import { registerSellerDetailRoute } from "./modules/sellers/seller-detail.route.js";
import { registerProfileRoute } from "./modules/seller/profile.route.js";
import { registerInventoryRoute } from "./modules/seller/inventory.route.js";
import { registerDashboardRoute } from "./modules/seller/dashboard.route.js";
import { registerReviewRoute } from "./modules/reviews/review.route.js";
import { registerChatRoute } from "./modules/chat/chat.route.js";
import { registerAssistantRoute } from "./modules/ai/assistant.route.js";
import { registerGoogleAuthRoute } from "./modules/auth/google.route.js";
import { registerLoginRoute } from "./modules/auth/login.route.js";
import { registerFirebaseAuthRoute } from "./modules/auth/firebase.route.js";
import { registerRegisterRoute } from "./modules/auth/register.route.js";
import { registerProductsRoute } from "./modules/products/list.route.js";
import { registerAssignedOrdersRoute } from "./modules/logistics/assigned.route.js";
import { registerPaymentsRoute } from "./modules/payments/payments.route.js";
import { registerRateLimit } from "./rate-limit.js";
import { registerSecurityHeaders } from "./security-headers.js";
import { registerCors } from "./cors.js";

export async function buildServer(): Promise<FastifyInstance> {
  const app = Fastify({
    logger: {
      level: process.env.LOG_LEVEL ?? "info",
      transport: undefined,
    },
    // Allow up to 2MB JSON bodies; POD submissions can be sizeable.
    bodyLimit: 2 * 1024 * 1024,
    trustProxy: true,
  });

  app.setErrorHandler((err, _request, reply) => {
    if (err instanceof AppError) {
      const headers: Record<string, string> = {};
      if (err instanceof NotImplementedError) {
        headers["x-stub-reason"] = err.stubReason;
      }
      reply
        .status(err.httpStatus)
        .headers(headers)
        .send({ error: err.code, message: err.message, details: err.details });
      return;
    }
    if (err && typeof err === "object" && "issues" in (err as Record<string, unknown>)) {
      reply.status(400).send({
        error: "validation",
        message: "request body failed validation",
        issues: (err as { issues: unknown }).issues,
      });
      return;
    }
    if ("validation" in (err as FastifyError)) {
      const fe = err as FastifyError;
      reply.status(400).send({
        error: "validation",
        message: fe.message,
      });
      return;
    }
    const code = (err as { code?: string }).code;
    if (code === "FST_ERR_CTP_INVALID_JSON_BODY" || code === "FST_ERR_CTP_EMPTY_JSON_BODY") {
      reply.status(400).send({
        error: "validation",
        message: "request body is not valid JSON",
      });
      return;
    }
    app.log.error({ err }, "unhandled");
    reply.status(500).send({ error: "internal", message: "internal server error" });
  });

  registerSecurityHeaders(app);
  registerCors(app);
  await registerRateLimit(app);

  // Raw body capture (used by the payment webhook to verify HMAC signatures
  // against the exact bytes the provider signed). Must be registered before
  // the routes that opt into it.
  await app.register(fastifyRawBody, { global: false });

  app.get("/healthz", async () => ({ ok: true }));
  app.get("/api/v1/healthz", async () => ({ ok: true, version: "1.0.0" }));

  // Public
  await registerGoogleAuthRoute(app);
  await registerLoginRoute(app);
  await registerFirebaseAuthRoute(app);
  await registerRegisterRoute(app);
  await registerAssistantRoute(app); // includes /api/v1/ai/status
  await registerPaymentsRoute(app); // collect (auth) + webhook (signed)

  // Authenticated
  await registerProductsRoute(app);
  await registerCheckoutRoute(app);
  await registerPodRoute(app);
  await registerAssignedOrdersRoute(app);
  await registerNearbyRoute(app);
  await registerSellerDetailRoute(app);
  await registerProfileRoute(app);
  await registerInventoryRoute(app);
  await registerDashboardRoute(app);
  await registerReviewRoute(app);
  await registerChatRoute(app);

  return app;
}

export async function startServer(): Promise<FastifyInstance> {
  // Honor a local .env (gitignored) so dev/`node dist/server.js` pick up
  // keys without exporting them. Explicit environment always wins.
  loadEnvFile();

  if (!process.env.JWT_SECRET) {
    process.env.JWT_SECRET = "dev-secret-do-not-use-in-prod-min-32-chars-long-please";
  }
  if (!process.env.DATABASE_URL) {
    process.env.DATABASE_URL = "postgres://app:app@127.0.0.1:5433/scottstechx";
  }
  const applied = await runMigrations();
  console.log("[migrate] applied:", applied);
  const app = await buildServer();
  const port = Number(process.env.PORT ?? 3001);
  const host = process.env.HOST ?? "0.0.0.0";
  await app.listen({ port, host });
  return app;
}

const entry = process.argv[1];
const isMain = entry != null && import.meta.url === pathToFileURL(entry).href;
if (isMain) {
  startServer().catch((err) => {
    console.error("server failed to start:", err);
    process.exit(1);
  });
}
