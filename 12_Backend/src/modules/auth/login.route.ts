/**
 * POST /api/v1/auth/login — phone + password login.
 *
 * Looks up a user by phone, verifies the scrypt password hash, and returns
 * the same HS256 JWT the rest of the API expects (plus userId, role, tier
 * and expiry so the Android client can store and display them).
 *
 * The `role` field sent by the client is accepted but ignored: the server is
 * authoritative about a user's role (stored on the users row). The returned
 * token carries the real role claim.
 *
 * NOTE (production hardening): the lookup runs inside withTransaction with a
 * null userId, mirroring google.route.ts. In the embedded-postgres dev/test
 * environment the connection is the cluster superuser, so RLS is bypassed. In
 * a hardened deployment, move this lookup behind a service role or a
 * SECURITY DEFINER function so `users_self_select` RLS doesn't hide buyer
 * rows from the anonymous login context.
 */

import { z } from "zod";
import type { FastifyInstance } from "fastify";
import { signToken, type AuthUser, type UserRole } from "../../auth.js";
import { withTransaction } from "../../db.js";
import { UnauthorizedError } from "../../errors.js";
import { verifyPassword } from "./password.js";
import { computeTrustTier } from "../trust/tier.js";

export const loginBodySchema = z.object({
  phone: z.string().min(6).max(30),
  password: z.string().min(1).max(200),
  role: z.string().optional(),
});

export type LoginBody = z.infer<typeof loginBodySchema>;

export type LoginResult = {
  token: string;
  userId: string;
  role: UserRole;
  trustTier: string;
  expiresAt: string;
};

export async function login(body: LoginBody): Promise<LoginResult> {
  const user = await withTransaction({ userId: null, role: null }, async (c) => {
    const r = await c.query<{
      id: string;
      email: string;
      role: UserRole;
      password_hash: string | null;
      is_active: boolean;
    }>(
      `SELECT id, email, role, password_hash, is_active
         FROM users
        WHERE phone = $1
        LIMIT 1`,
      [body.phone],
    );
    return r.rows[0] ?? null;
  });

  // Same message for unknown phone and wrong password — no user enumeration.
  if (!user || !user.password_hash) {
    throw new UnauthorizedError("invalid phone or password");
  }
  if (!verifyPassword(body.password, user.password_hash)) {
    throw new UnauthorizedError("invalid phone or password");
  }
  if (!user.is_active) {
    throw new UnauthorizedError("account is disabled");
  }

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

export async function registerLoginRoute(app: FastifyInstance): Promise<void> {
  app.post("/api/v1/auth/login", async (request, reply) => {
    let body: LoginBody;
    try {
      body = loginBodySchema.parse(request.body);
    } catch (err) {
      reply.status(400).send({
        error: "validation",
        message: "request body failed validation",
        issues: (err as { issues?: unknown }).issues,
      });
      return;
    }
    try {
      const result = await login(body);
      reply.send(result);
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        reply.status(err.httpStatus).send({ error: err.code, message: err.message });
        return;
      }
      throw err;
    }
  });
}
