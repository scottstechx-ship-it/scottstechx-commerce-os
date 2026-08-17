import { z } from "zod";
import type { FastifyInstance } from "fastify";
import { requireAuth, getAuthUser } from "../../auth.js";
import {
  AiDisabledError,
  callAi,
  getProvider,
  persistSuggestion,
  type SuggestionType,
} from "./assistant.service.js";
import { withTransaction } from "../../db.js";
import { searchCatalogForQuery, formatCatalogHits } from "./catalog.js";

const sellerSuggestSchema = z.object({
  type: z.enum([
    "product_description",
    "auto_price",
    "category",
    "inventory_warning",
  ]),
  draft: z.record(z.string(), z.unknown()),
  context: z.record(z.string(), z.unknown()).optional(),
});

const customerChatSchema = z.object({
  sessionId: z.string().min(1).max(100),
  message: z.string().min(1).max(2000),
  history: z
    .array(
      z.object({
        role: z.enum(["user", "assistant", "system"]),
        content: z.string(),
      }),
    )
    .max(40)
    .optional(),
});

const reasonSchema = z.object({
  sellerId: z.string().uuid(),
  context: z.record(z.string(), z.unknown()).optional(),
});

export async function registerAssistantRoute(app: FastifyInstance): Promise<void> {
  app.get("/api/v1/ai/status", async (_req, reply) => {
    const provider = getProvider();
    reply.send({ enabled: !!provider, provider: provider ?? null });
  });

  app.post(
    "/api/v1/ai/seller-suggest",
    { preHandler: requireAuth },
    async (request, reply) => {
      const user = getAuthUser(request);
      if (user.role !== "seller") {
        reply.status(403).send({ error: "forbidden", message: "sellers only" });
        return;
      }
      const body = sellerSuggestSchema.parse(request.body);
      try {
        const out = await callAi({
          type: body.type as SuggestionType,
          draft: body.draft,
          context: body.context,
        });
        await persistSuggestion({
          userId: user.id,
          role: user.role,
          sellerId: user.id,
          suggestionType: body.type as SuggestionType,
          payload: { input: body, output: out },
          provider: out.provider,
        });
        reply.send(out);
      } catch (err) {
        if (err instanceof AiDisabledError) {
          reply
            .status(503)
            .send({ error: "ai_disabled", message: err.message });
          return;
        }
        throw err;
      }
    },
  );

  app.post(
    "/api/v1/ai/customer-chat",
    { preHandler: requireAuth },
    async (request, reply) => {
      const user = getAuthUser(request);
      const body = customerChatSchema.parse(request.body);
      try {
        const catalogHits = await searchCatalogForQuery(user, body.message);
        const out = await callAi({
          type: "customer_chat",
          draft: { message: body.message },
          history: body.history,
          grounding: formatCatalogHits(catalogHits),
        });
        await withTransaction(
          { userId: user.id, role: user.role },
          async (c) => {
            await c.query(
              `INSERT INTO chat_messages (sender_user_id, role, content, session_id)
               VALUES ($1, 'buyer', $2, $3)`,
              [user.id, body.message, body.sessionId],
            );
            await c.query(
              `INSERT INTO chat_messages (sender_user_id, role, content, session_id)
               VALUES ($1, 'ai', $2, $3)`,
              [user.id, out.suggestion, body.sessionId],
            );
          },
        );
        await persistSuggestion({
          userId: user.id,
          role: user.role,
          suggestionType: "customer_chat",
          payload: { message: body.message, reply: out.suggestion },
          provider: out.provider,
        });
        reply.send({ reply: out.suggestion, provider: out.provider });
      } catch (err) {
        if (err instanceof AiDisabledError) {
          reply
            .status(503)
            .send({ error: "ai_disabled", message: err.message });
          return;
        }
        throw err;
      }
    },
  );

  app.post(
    "/api/v1/ai/reason",
    { preHandler: requireAuth },
    async (request, reply) => {
      const user = getAuthUser(request);
      const body = reasonSchema.parse(request.body);
      try {
        const out = await callAi({
          type: "trust_reasoning",
          draft: { sellerId: body.sellerId },
          context: body.context,
        });
        await persistSuggestion({
          userId: user.id,
          role: user.role,
          suggestionType: "trust_reasoning",
          payload: { sellerId: body.sellerId, context: body.context, output: out },
          provider: out.provider,
        });
        reply.send({
          trustReasoning: out.suggestion,
          rankReasoning: out.suggestion,
          recommendation: out.suggestion,
          provider: out.provider,
          confidence: out.confidence,
        });
      } catch (err) {
        if (err instanceof AiDisabledError) {
          reply
            .status(503)
            .send({ error: "ai_disabled", message: err.message });
          return;
        }
        throw err;
      }
    },
  );
}
