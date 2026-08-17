/**
 * POST /api/v1/auth/firebase — verify a Firebase Auth ID token and return our
 * own HS256 JWT.
 *
 * The Android client signs the user up / in via Firebase Auth (email+password
 * signup, Google Sign-In, or phone) and sends the resulting ID token here.
 * We verify it with the Admin SDK and upsert a local `users` row, keyed by
 * `users.firebase_uid` (falling back to email), then issue the same JWT the
 * rest of the API expects.
 *
 * The optional `role` body field is honored only for NEW users (it seeds the
 * local role + profile row); for existing users the stored role wins.
 */

import { z } from "zod";
import type { FastifyInstance } from "fastify";
import type { PoolClient } from "pg";
import { signToken, type AuthUser, type UserRole } from "../../auth.js";
import { withTransaction } from "../../db.js";
import { computeTrustTier } from "../trust/tier.js";
import { isFirebaseConfigured, verifyFirebaseIdToken, type FirebaseIdentity } from "./firebase.js";

export const firebaseAuthBodySchema = z.object({
  idToken: z.string().min(20),
  role: z.enum(["buyer", "driver", "seller"]).optional(),
});

export type FirebaseAuthBody = z.infer<typeof firebaseAuthBodySchema>;

export type FirebaseAuthResult = {
  token: string;
  userId: string;
  role: UserRole;
  email: string | null;
  trustTier: string;
  expiresAt: string;
};

function placeholderEmail(uid: string): string {
  return `${uid}@firebase.scottstechx.local`;
}

async function upsertFromFirebase(
  client: PoolClient,
  identity: FirebaseIdentity,
  requestedRole: UserRole,
): Promise<{ id: string; email: string; role: UserRole }> {
  const email = identity.email ?? placeholderEmail(identity.uid);
  const displayName = identity.name?.trim() || "ScottsTechX user";

  // 1. Match by firebase_uid.
  const byUid = await client.query<{ id: string; email: string; role: UserRole }>(
    `SELECT id, email, role FROM users WHERE firebase_uid = $1`,
    [identity.uid],
  );
  if (byUid.rowCount && byUid.rowCount > 0) {
    return byUid.rows[0]!;
  }

  // 2. Match by email (a user may have signed up via another method first).
  const byEmail = await client.query<{ id: string; email: string; role: UserRole }>(
    `SELECT id, email, role FROM users WHERE email = $1`,
    [email],
  );
  if (byEmail.rowCount && byEmail.rowCount > 0) {
    const row = byEmail.rows[0]!;
    await client.query(`UPDATE users SET firebase_uid = $1 WHERE id = $2`, [identity.uid, row.id]);
    return row;
  }

  // 3. Create a new user.
  const inserted = await client.query<{ id: string; email: string; role: UserRole }>(
    `INSERT INTO users (email, display_name, role, password_hash, phone, firebase_uid)
     VALUES ($1, $2, $3, NULL, $4, $5)
     RETURNING id, email, role`,
    [email, displayName, requestedRole, identity.phoneNumber ?? null, identity.uid],
  );

  const id = inserted.rows[0]!.id;
  if (requestedRole === "seller") {
    await client.query(
      `INSERT INTO seller_profiles (user_id, business_name)
       VALUES ($1, $2) ON CONFLICT (user_id) DO NOTHING`,
      [id, `${displayName}'s Shop`],
    );
  }
  if (requestedRole === "driver") {
    await client.query(
      `INSERT INTO driver_profiles (user_id) VALUES ($1) ON CONFLICT (user_id) DO NOTHING`,
      [id],
    );
  }
  return inserted.rows[0]!;
}

export async function firebaseAuth(body: FirebaseAuthBody): Promise<FirebaseAuthResult> {
  if (!isFirebaseConfigured()) {
    throw new FirebaseAuthDisabledError();
  }
  const identity = await verifyFirebaseIdToken(body.idToken);
  const requestedRole: UserRole = body.role ?? "buyer";

  const user = await withTransaction({ userId: null, role: null }, (client) =>
    upsertFromFirebase(client, identity, requestedRole),
  );

  const authUser: AuthUser = { id: user.id, role: user.role, email: user.email };
  const token = await signToken(authUser);
  const trustTier = await computeTrustTier(user.id);

  return {
    token,
    userId: user.id,
    role: user.role,
    email: user.email,
    trustTier,
    expiresAt: new Date(Date.now() + 3600 * 1000).toISOString(),
  };
}

export class FirebaseAuthDisabledError extends Error {
  readonly code = "firebase_auth_disabled";
  constructor() {
    super("Firebase Auth is not configured on this server (set FIREBASE_ADMIN_CREDENTIAL_JSON or FIREBASE_PROJECT_ID)");
    this.name = "FirebaseAuthDisabledError";
  }
}

export async function registerFirebaseAuthRoute(app: FastifyInstance): Promise<void> {
  app.post("/api/v1/auth/firebase", async (request, reply) => {
    if (!isFirebaseConfigured()) {
      reply.status(503).send({
        error: "firebase_auth_disabled",
        message:
          "Firebase Auth is not configured on this server (set FIREBASE_ADMIN_CREDENTIAL_JSON or FIREBASE_PROJECT_ID)",
      });
      return;
    }
    let body: FirebaseAuthBody;
    try {
      body = firebaseAuthBodySchema.parse(request.body);
    } catch (err) {
      reply.status(400).send({
        error: "validation",
        message: "request body failed validation",
        issues: (err as { issues?: unknown }).issues,
      });
      return;
    }
    try {
      const result = await firebaseAuth(body);
      reply.send(result);
    } catch (err) {
      if (err instanceof FirebaseAuthDisabledError) {
        reply.status(503).send({ error: err.code, message: err.message });
        return;
      }
      // Invalid/expired Firebase token → 401.
      if (err && typeof err === "object" && "code" in (err as Record<string, unknown>)) {
        const code = (err as { code?: string }).code;
        if (code === "auth/id-token-expired" || code === "auth/argument-error" || code === "auth/id-token-revoked") {
          reply.status(401).send({ error: "unauthorized", message: "invalid or expired Firebase token" });
          return;
        }
      }
      throw err;
    }
  });
}
