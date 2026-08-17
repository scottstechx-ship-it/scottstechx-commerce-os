/**
 * Firebase Admin SDK wrapper.
 *
 * Verifies Firebase Auth ID tokens (issued by the Android client's Firebase
 * Auth SDK for email/password signup, Google Sign-In, or phone auth) and maps
 * them to a local identity. Firebase Auth handles signup + Google OAuth on
 * the device; the backend trusts the verified token and upserts the local
 * `users` row (see firebase.route.ts).
 *
 * Credential resolution (in priority order):
 *   1. FIREBASE_ADMIN_CREDENTIAL_JSON — inline service-account JSON (handy for
 *      local dev and non-GCP hosts).
 *   2. GOOGLE_APPLICATION_CREDENTIALS — path to a service-account JSON file.
 *   3. Application Default Credentials — automatic on Cloud Run / GCE.
 *
 * Set FIREBASE_PROJECT_ID to advertise "Firebase Auth enabled" on hosts where
 * credentials come from ADC (Cloud Run); the actual credential is resolved by
 * the Admin SDK from the runtime environment.
 */

import { initializeApp, cert, getApps, type App } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";

let app: App | null = null;

export function isFirebaseConfigured(): boolean {
  return !!(
    process.env.FIREBASE_ADMIN_CREDENTIAL_JSON ||
    process.env.GOOGLE_APPLICATION_CREDENTIALS ||
    process.env.FIREBASE_PROJECT_ID
  );
}

function getApp(): App {
  if (app) return app;
  const existing = getApps();
  if (existing.length > 0) {
    app = existing[0]!;
    return app;
  }
  if (process.env.FIREBASE_ADMIN_CREDENTIAL_JSON) {
    const raw = JSON.parse(process.env.FIREBASE_ADMIN_CREDENTIAL_JSON) as {
      project_id: string;
      client_email: string;
      private_key: string;
    };
    app = initializeApp({
      credential: cert({
        projectId: raw.project_id,
        clientEmail: raw.client_email,
        privateKey: raw.private_key,
      }),
    });
    return app;
  }
  // GOOGLE_APPLICATION_CREDENTIALS or ADC.
  app = initializeApp();
  return app;
}

export type FirebaseIdentity = {
  uid: string;
  email?: string;
  name?: string;
  phoneNumber?: string;
};

export async function verifyFirebaseIdToken(idToken: string): Promise<FirebaseIdentity> {
  const decoded = await getAuth(getApp()).verifyIdToken(idToken);
  return {
    uid: decoded.uid,
    email: decoded.email,
    name: decoded.name,
    phoneNumber: decoded.phone_number,
  };
}
