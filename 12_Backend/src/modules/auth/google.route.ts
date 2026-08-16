/**
 * Google OAuth: verify an ID token from Google's JWKS, look up or create
 * the user by email, return our own HS256 JWT.
 *
 * If GOOGLE_CLIENT_ID is unset, the route returns 503 with code
 * `google_auth_disabled` so the Android client can surface a friendly
 * message instead of pretending success.
 *
 * Audience: when GOOGLE_CLIENT_ID is set, the `aud` claim of the token
 * must match. When unset, audience is not checked — that allows local
 * development with fake JWKS.
 */

import { z } from "zod";
import {
  createRemoteJWKSet,
  jwtVerify,
  type JWTPayload,
} from "jose";
import type { FastifyInstance } from "fastify";
import { signToken, type AuthUser, type UserRole } from "../../auth.js";
import { withTransaction } from "../../db.js";
import { computeTrustTier } from "../trust/tier.js";

let _jwks: ReturnType<typeof createRemoteJWKSet> | null = null;
function getJwks() {
  if (!_jwks) {
    _jwks = createRemoteJWKSet(
      new URL("https://www.googleapis.com/oauth2/v3/certs"),
    );
  }
  return _jwks;
}

export class GoogleAuthDisabledError extends Error {
  readonly code = "google_auth_disabled";
  constructor() {
    super("Google sign-in is not configured on this server (set GOOGLE_CLIENT_ID)");
    this.name = "GoogleAuthDisabledError";
  }
}

export const googleAuthBodySchema = z.object({
  idToken: z.string().min(10),
  role: z.enum(["buyer", "driver", "seller"]).optional(),
});

export type GoogleAuthBody = z.infer<typeof googleAuthBodySchema>;

export type GoogleAuthResult = {
  token: string;
  userId: string;
  role: UserRole;
  email: string;
  expiresAt: string;
  trustTier: string;
};

export async function googleAuth(body: GoogleAuthBody): Promise<GoogleAuthResult> {
  const clientId = process.env.GOOGLE_CLIENT_ID;
  if (!clientId) throw new GoogleAuthDisabledError();

  const verifyOpts: Parameters<typeof jwtVerify>[2] = {
    issuer: ["https://accounts.google.com", "accounts.google.com"],
    audience: clientId,
  };
  const { payload } = await jwtVerify(body.idToken, getJwks(), verifyOpts);

  const email = (payload as JWTPayload).email;
  const sub = (payload as JWTPayload).sub;
  if (typeof email !== "string" || typeof sub !== "string") {
    throw new Error("idToken missing email/sub");
  }
  const displayName =
    typeof (payload as JWTPayload).name === "string"
      ? (payload as JWTPayload).name
      : email.split("@")[0]!;

  const requestedRole: UserRole = body.role ?? "buyer";

  const user = await withTransaction(
    { userId: null, role: null },
    async (c) => {
      const existing = await c.query<{
        id: string;
        email: string;
        display_name: string;
        role: UserRole;
      }>(
        `SELECT id, email, display_name, role FROM users WHERE email = $1`,
        [email],
      );
      if (existing.rowCount && existing.rowCount > 0) {
        return existing.rows[0]!;
      }
      // Create a new user. password_hash is null — Google login doesn't need one.
      const inserted = await c.query<{
        id: string;
        email: string;
        display_name: string;
        role: UserRole;
      }>(
        `INSERT INTO users (email, display_name, role, password_hash)
         VALUES ($1, $2, $3, NULL)
         RETURNING id, email, display_name, role`,
        [email, displayName, requestedRole],
      );
      // If they're a seller, auto-create a seller_profiles row.
      if (requestedRole === "seller") {
        await c.query(
          `INSERT INTO seller_profiles (user_id, business_name)
           VALUES ($1, $2)
           ON CONFLICT (user_id) DO NOTHING`,
          [inserted.rows[0]!.id, `${displayName}'s Shop`],
        );
      }
      if (requestedRole === "driver") {
        await c.query(
          `INSERT INTO driver_profiles (user_id) VALUES ($1) ON CONFLICT DO NOTHING`,
          [inserted.rows[0]!.id],
        );
      }
      return inserted.rows[0]!;
    },
  );

  const authUser: AuthUser = { id: user.id, role: user.role, email: user.email };
  const token = await signToken(authUser);
  // Compute approximate trust tier from rating_avg if available.
  const trustTier = await computeTrustTier(user.id);
  return {
    token,
    userId: user.id,
    role: user.role,
    email: user.email,
    expiresAt: new Date(Date.now() + 3600 * 1000).toISOString(),
    trustTier,
  };
}

export async function registerGoogleAuthRoute(app: FastifyInstance): Promise<void> {
  app.post("/api/v1/auth/google", async (request, reply) => {
    // Check the disabled state FIRST so the caller gets a clean 503
    // instead of a misleading 400 when the server isn't configured.
    if (!process.env.GOOGLE_CLIENT_ID) {
      reply.status(503).send({
        error: "google_auth_disabled",
        message: "Google sign-in is not configured on this server (set GOOGLE_CLIENT_ID)",
      });
      return;
    }
    let body: GoogleAuthBody;
    try {
      body = googleAuthBodySchema.parse(request.body);
    } catch (err) {
      reply.status(400).send({
        error: "validation",
        message: "request body failed validation",
        issues: (err as { issues?: unknown }).issues,
      });
      return;
    }
    try {
      const result = await googleAuth(body);
      reply.send(result);
    } catch (err) {
      if (err instanceof GoogleAuthDisabledError) {
        reply.status(503).send({ error: err.code, message: err.message });
        return;
      }
      throw err;
    }
  });
}
