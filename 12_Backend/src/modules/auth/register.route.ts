/**
 * POST /api/v1/auth/register — phone + password signup.
 *
 * Creates a local user (synthesizing a stable placeholder email from the
 * phone number, since `users.email` is NOT NULL and phone users don't have
 * one), hashes the password with scrypt, creates the role profile row when
 * the caller asks to register as a seller/driver, and returns the same JWT
 * shape as /auth/login so the client can start immediately.
 */

import { z } from "zod";
import type { FastifyInstance } from "fastify";
import { signToken, type AuthUser, type UserRole } from "../../auth.js";
import { withTransaction } from "../../db.js";
import { ConflictError } from "../../errors.js";
import { hashPassword } from "./password.js";
import { computeTrustTier } from "../trust/tier.js";

export const registerBodySchema = z.object({
  phone: z.string().min(6).max(30),
  password: z.string().min(8).max(200),
  displayName: z.string().min(1).max(120).optional(),
  role: z.enum(["buyer", "driver", "seller"]).default("buyer"),
});

export type RegisterBody = z.infer<typeof registerBodySchema>;

export type RegisterResult = {
  token: string;
  userId: string;
  role: UserRole;
  trustTier: string;
  expiresAt: string;
};

function emailForPhone(phone: string): string {
  const digits = phone.replace(/\D/g, "");
  return `${digits}@phone.scottstechx.local`;
}

export async function register(body: RegisterBody): Promise<RegisterResult> {
  const role: UserRole = body.role;
  const displayName = body.displayName?.trim() || "ScottsTechX user";

  const user = await withTransaction({ userId: null, role: null }, async (c) => {
    const existing = await c.query<{ id: string }>(
      `SELECT id FROM users WHERE phone = $1`,
      [body.phone],
    );
    if ((existing.rowCount ?? 0) > 0) {
      throw new ConflictError("an account with this phone number already exists", { phone: body.phone });
    }

    const inserted = await c.query<{ id: string; email: string; role: UserRole }>(
      `INSERT INTO users (email, display_name, role, password_hash, phone)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING id, email, role`,
      [emailForPhone(body.phone), displayName, role, hashPassword(body.password), body.phone],
    );

    const id = inserted.rows[0]!.id;
    if (role === "seller") {
      await c.query(
        `INSERT INTO seller_profiles (user_id, business_name)
         VALUES ($1, $2) ON CONFLICT (user_id) DO NOTHING`,
        [id, `${displayName}'s Shop`],
      );
    }
    if (role === "driver") {
      await c.query(
        `INSERT INTO driver_profiles (user_id) VALUES ($1) ON CONFLICT (user_id) DO NOTHING`,
        [id],
      );
    }
    return inserted.rows[0]!;
  });

  const authUser: AuthUser = { id: user.id, role: user.role, email: user.email };
  const token = await signToken(authUser);
  const trustTier = await computeTrustTier(user.id);

  return {
    token,
    userId: user.id,
    role: user.role,
    trustTier,
    expiresAt: new Date(Date.now() + 3600 * 1000).toISOString(),
  };
}

export async function registerRegisterRoute(app: FastifyInstance): Promise<void> {
  app.post("/api/v1/auth/register", async (request, reply) => {
    let body: RegisterBody;
    try {
      body = registerBodySchema.parse(request.body);
    } catch (err) {
      reply.status(400).send({
        error: "validation",
        message: "request body failed validation",
        issues: (err as { issues?: unknown }).issues,
      });
      return;
    }
    try {
      const result = await register(body);
      reply.status(201).send(result);
    } catch (err) {
      if (err instanceof ConflictError) {
        reply.status(409).send({ error: err.code, message: err.message, details: err.details });
        return;
      }
      throw err;
    }
  });
}
