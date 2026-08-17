/**
 * AI assistant — provider-agnostic wrapper around the configured LLM.
 *
 * Provider chosen by AI_PROVIDER env:
 *   - "openai"    -> POST https://api.openai.com/v1/chat/completions
 *   - "anthropic" -> POST https://api.anthropic.com/v1/messages
 *   - "gemini"    -> POST https://generativelanguage.googleapis.com/v1beta/models/...
 *
 * If LLM_API_KEY is unset, callers MUST treat the request as 503 'ai_disabled'
 * (see assistant.route.ts). The functions in this file throw
 * AiDisabledError for that case so callers don't have to know about env vars.
 *
 * Trust boundary: every call is recorded in ai_suggestions so we have an
 * audit trail of what the AI told which user. The audit insert uses the
 * caller's user_id so RLS keeps the row visible only to that user.
 */

import { withTransaction } from "../../db.js";

export class AiDisabledError extends Error {
  readonly code = "ai_disabled";
  constructor() {
    super("AI features are disabled on this server (set LLM_API_KEY)");
    this.name = "AiDisabledError";
  }
}

export type AiProvider = "openai" | "anthropic" | "gemini" | "nvidia";

export type SuggestionType =
  | "product_description"
  | "auto_price"
  | "category"
  | "inventory_warning"
  | "customer_chat"
  | "trust_reasoning";

export type SuggestionInput = {
  type: SuggestionType;
  draft: Record<string, unknown>;
  context?: Record<string, unknown>;
  history?: Array<{ role: "user" | "assistant" | "system"; content: string }>;
  /** Pre-fetched catalog matches to ground the answer (customer_chat). */
  grounding?: string;
};

export type SuggestionOutput = {
  suggestion: string;
  reasoning: string;
  confidence: number;
  provider: AiProvider;
};

export function getProvider(): AiProvider | null {
  const p = (process.env.AI_PROVIDER ?? "").toLowerCase();
  // Only report a provider as enabled when its key is actually present, so
  // /api/v1/ai/status doesn't claim "enabled" for a keyless deployment.
  if (p === "nvidia" && process.env.NVIDIA_API_KEY) return "nvidia";
  if (
    (p === "openai" || p === "anthropic" || p === "gemini") &&
    process.env.LLM_API_KEY
  ) {
    return p;
  }
  // Fall back to a provider if a matching key is present.
  if (process.env.NVIDIA_API_KEY) return "nvidia";
  if (process.env.LLM_API_KEY) return "openai";
  return null;
}

function systemPromptFor(type: SuggestionType): string {
  switch (type) {
    case "product_description":
      return "You are a friendly product copywriter for an East African marketplace. " +
        "Write a short, vivid, 2-3 sentence product description in plain English. " +
        "Avoid jargon. Include a sensory detail or use case. Do not invent facts " +
        "the seller did not provide.";
    case "auto_price":
      return "You are a pricing strategist for an East African marketplace. " +
        "Given a product draft (title, description, category, suggested price, " +
        "neighborhood), propose a price in UGX and explain your reasoning in " +
        "1-2 short paragraphs. Be honest about uncertainty.";
    case "category":
      return "You are a marketplace categorizer. Pick the single best product " +
        "category from: Groceries, Electronics, Fashion, Home, Beauty, Crafts, " +
        "Services, Other. Reply with the category name and a 1-line reason.";
    case "inventory_warning":
      return "You are an inventory analyst. Look at the seller's stock levels and " +
        "recent sales. Flag any items that look low or stale. Reply in plain " +
        "English, no more than 4 bullet points.";
    case "customer_chat":
      return "You are a helpful, warm shopping assistant for an East African " +
        "marketplace. Answer in 1-3 short sentences. If the user is asking about " +
        "products, answer from the provided catalog matches (name, price, stock) " +
        "and say so when nothing matches. Be honest about what you don't know. " +
        "Never claim to be a human.";
    case "trust_reasoning":
      return "You are a trust analyst. Given the seller profile data, produce a " +
        "1-paragraph plain-language explanation of why this seller has the rank " +
        "they do, including which signals boosted it and which dragged it down.";
  }
}

export async function callAi(input: SuggestionInput): Promise<SuggestionOutput> {
  const provider = getProvider();
  const apiKey = provider === "nvidia" ? process.env.NVIDIA_API_KEY : process.env.LLM_API_KEY;
  if (!provider || !apiKey) throw new AiDisabledError();

  const sys = systemPromptFor(input.type);
  const context = input.grounding
    ? { ...(input.context ?? {}), catalog: input.grounding }
    : (input.context ?? {});
  const userText = JSON.stringify({ draft: input.draft, context });

  if (provider === "openai") {
    const r = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: process.env.AI_MODEL ?? "gpt-4o-mini",
        temperature: 0.4,
        messages: [
          { role: "system", content: sys },
          ...(input.history ?? []).map((m) => ({ role: m.role, content: m.content })),
          { role: "user", content: userText },
        ],
      }),
    });
    if (!r.ok) throw new Error(`openai ${r.status}: ${await r.text()}`);
    const data = (await r.json()) as {
      choices?: Array<{ message?: { content?: string } }>;
    };
    const suggestion = data.choices?.[0]?.message?.content?.trim() ?? "";
    return {
      suggestion,
      reasoning: "Based on the seller draft and marketplace context.",
      confidence: 0.75,
      provider,
    };
  }

  if (provider === "anthropic") {
    const r = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: {
        "x-api-key": apiKey,
        "anthropic-version": "2023-06-01",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: process.env.AI_MODEL ?? "claude-3-5-sonnet-latest",
        max_tokens: 512,
        system: sys,
        messages: [
          ...(input.history ?? []).map((m) => ({ role: m.role, content: m.content })),
          { role: "user", content: userText },
        ],
      }),
    });
    if (!r.ok) throw new Error(`anthropic ${r.status}: ${await r.text()}`);
    const data = (await r.json()) as {
      content?: Array<{ type: string; text?: string }>;
    };
    const suggestion = data.content?.find((b) => b.type === "text")?.text?.trim() ?? "";
    return {
      suggestion,
      reasoning: "Based on the seller draft and marketplace context.",
      confidence: 0.75,
      provider,
    };
  }

  // nvidia — OpenAI-compatible endpoint (NVIDIA NIM / Nemotron).
  if (provider === "nvidia") {
    const base = process.env.NVIDIA_BASE_URL ?? "https://integrate.api.nvidia.com/v1";
    const model = process.env.AI_MODEL ?? "nvidia/nemotron-3-ultra-550b-a55b";
    const body: Record<string, unknown> = {
      model,
      temperature: 1,
      top_p: 0.95,
      max_tokens: 16384,
      messages: [
        { role: "system", content: sys },
        ...(input.history ?? []).map((m) => ({ role: m.role, content: m.content })),
        { role: "user", content: userText },
      ],
    };
    if ((process.env.AI_REASONING ?? "") === "1") {
      body.extra_body = {
        chat_template_kwargs: { enable_thinking: true },
        reasoning_budget: 16384,
      };
    }
    const r = await fetch(`${base}/chat/completions`, {
      method: "POST",
      headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!r.ok) throw new Error(`nvidia ${r.status}: ${await r.text()}`);
    const data = (await r.json()) as {
      choices?: Array<{ message?: { content?: string } }>;
    };
    const suggestion = data.choices?.[0]?.message?.content?.trim() ?? "";
    return {
      suggestion,
      reasoning: "Based on the seller draft and marketplace context.",
      confidence: 0.75,
      provider,
    };
  }

  // gemini
  const model = process.env.AI_MODEL ?? "gemini-1.5-flash";
  const r = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: sys }] },
        contents: [
          {
            role: "user",
            parts: [
              {
                text:
                  (input.history ?? [])
                    .map((m) => `${m.role.toUpperCase()}: ${m.content}`)
                    .join("\n") +
                  "\nUSER: " +
                  userText,
              },
            ],
          },
        ],
        generationConfig: { temperature: 0.4, maxOutputTokens: 512 },
      }),
    },
  );
  if (!r.ok) throw new Error(`gemini ${r.status}: ${await r.text()}`);
  const data = (await r.json()) as {
    candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
  };
  const suggestion =
    data.candidates?.[0]?.content?.parts?.map((p) => p.text ?? "").join("").trim() ?? "";
  return {
    suggestion,
    reasoning: "Based on the seller draft and marketplace context.",
    confidence: 0.75,
    provider,
  };
}

export async function persistSuggestion(args: {
  userId: string;
  role: string;
  sellerId?: string | null;
  suggestionType: SuggestionType;
  payload: Record<string, unknown>;
  provider: AiProvider;
  accepted?: boolean;
}): Promise<void> {
  await withTransaction(
    { userId: args.userId, role: args.role },
    async (c) => {
      await c.query(
        `INSERT INTO ai_suggestions (seller_id, user_id, suggestion_type, payload, accepted, provider)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [
          args.sellerId ?? null,
          args.userId,
          args.suggestionType,
          JSON.stringify(args.payload),
          args.accepted ?? null,
          args.provider,
        ],
      );
    },
  );
}
