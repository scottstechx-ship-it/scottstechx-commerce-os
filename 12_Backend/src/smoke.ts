/**
 * Smoke check — boots the real server (embedded Postgres + migrations),
 * then curls every public endpoint and prints a one-line PASS/FAIL
 * summary. Exits non-zero on any failure.
 *
 * Designed for hosted-server verification: `./npm run smoke` will fail
 * loudly if any endpoint is misconfigured.
 */

import { spawn } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import EmbeddedPostgres from "embedded-postgres";

const PORT = Number(process.env.SMOKE_PORT ?? 3099);
const HOST = "127.0.0.1";
const BASE = `http://${HOST}:${PORT}`;

async function get(path: string, headers: Record<string, string> = {}): Promise<{ status: number; body: string; headers: Headers }> {
  const r = await fetch(`${BASE}${path}`, { headers });
  return { status: r.status, body: await r.text(), headers: r.headers };
}

async function postJson(path: string, body: unknown, headers: Record<string, string> = {}): Promise<{ status: number; body: string }> {
  const r = await fetch(`${BASE}${path}`, {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  return { status: r.status, body: await r.text() };
}

async function waitForReady(maxMs = 30000): Promise<boolean> {
  const deadline = Date.now() + maxMs;
  while (Date.now() < deadline) {
    try {
      const r = await get("/healthz");
      if (r.status === 200) return true;
    } catch {
      /* not up yet */
    }
    await delay(250);
  }
  return false;
}

async function main(): Promise<number> {
  console.log(`[smoke] starting server on :${PORT}`);

  // Boot a throwaway embedded Postgres unless DATABASE_URL is already set,
  // so `npm run smoke` works on a machine with no Docker or system Postgres.
  let pg: EmbeddedPostgres | null = null;
  let databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    const pgPort = 40000 + Math.floor(Math.random() * 5000);
    const dataDir = mkdtempSync(join(tmpdir(), "scottstechx-smoke-pg-"));
    pg = new EmbeddedPostgres({
      databaseDir: dataDir,
      user: "app",
      password: "app",
      port: pgPort,
      persistent: false,
    });
    await pg.initialise();
    await pg.start();
    await pg.createDatabase("smoke");
    databaseUrl = `postgres://app:app@127.0.0.1:${pgPort}/smoke`;
    console.log(`[smoke] embedded Postgres on :${pgPort}`);
  }

  const child = spawn(
    process.execPath,
    ["--import", "tsx", "src/server.ts"],
    {
      env: {
        ...process.env,
        PORT: String(PORT),
        HOST,
        JWT_SECRET: process.env.JWT_SECRET ?? "smoke-secret-must-be-at-least-32-chars-long-ok",
        DATABASE_URL: databaseUrl,
        NODE_ENV: "test",
      },
      stdio: ["ignore", "inherit", "inherit"],
    },
  );

  // Trap signals so the spawned server dies when we do.
  const cleanup = () => {
    try {
      child.kill();
    } catch {
      /* ignore */
    }
  };
  process.on("exit", cleanup);
  process.on("SIGINT", () => {
    cleanup();
    if (pg) {
      void pg.stop().catch(() => {});
    }
    process.exit(130);
  });

  const ready = await waitForReady();
  if (!ready) {
    console.error("[smoke] FAIL: server did not become ready in time");
    cleanup();
    if (pg) {
      try {
        await pg.stop();
      } catch {
        /* best effort */
      }
    }
    return 1;
  }

  const checks: Array<{ name: string; status: number; pass: boolean }> = [];

  // 1. healthz
  const h = await get("/healthz");
  checks.push({ name: "GET /healthz", status: h.status, pass: h.status === 200 });

  // 2. /api/v1/healthz
  const h2 = await get("/api/v1/healthz");
  checks.push({ name: "GET /api/v1/healthz", status: h2.status, pass: h2.status === 200 });

  // 3. /api/v1/ai/status (no auth, should always respond)
  const ai = await get("/api/v1/ai/status");
  const aiBody = JSON.parse(ai.body) as { enabled: boolean };
  checks.push({
    name: "GET /api/v1/ai/status",
    status: ai.status,
    pass: ai.status === 200 && typeof aiBody.enabled === "boolean",
  });

  // 4. /api/v1/auth/google without a key → 503
  const g = await postJson("/api/v1/auth/google", { idToken: "fake" });
  checks.push({
    name: "POST /api/v1/auth/google (no key) -> 503",
    status: g.status,
    pass: g.status === 503,
  });

  // 5. unauthenticated /api/v1/sellers/nearby → 401
  const u = await get("/api/v1/sellers/nearby?lat=0&lng=0");
  checks.push({
    name: "GET /api/v1/sellers/nearby (no auth) -> 401",
    status: u.status,
    pass: u.status === 401,
  });

  // 6. security headers present on /healthz
  checks.push({
    name: "security headers on /healthz",
    status: 200,
    pass:
      h.headers.get("x-content-type-options") === "nosniff" &&
      h.headers.get("referrer-policy") === "no-referrer",
  });

  // Report.
  console.log("\n[smoke] results:");
  for (const c of checks) {
    console.log(
      `  ${c.pass ? "PASS" : "FAIL"}  ${c.name}  (status=${c.status})`,
    );
  }
  const allPass = checks.every((c) => c.pass);
  console.log(
    `\n[smoke] ${allPass ? "PASS" : "FAIL"} (${checks.filter((c) => c.pass).length}/${checks.length})`,
  );

  cleanup();
  await new Promise((resolve) => setTimeout(resolve, 200));
  if (pg) {
    try {
      await pg.stop();
    } catch {
      /* best effort */
    }
  }
  return allPass ? 0 : 1;
}

main().then(
  (code) => process.exit(code),
  (err) => {
    console.error("[smoke] crashed:", err);
    process.exit(2);
  },
);
