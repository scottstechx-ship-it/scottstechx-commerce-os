/**
 * `npm run dev:embedded` — one-command local dev.
 *
 * Boots a throwaway embedded Postgres (no Docker / system Postgres needed),
 * points DATABASE_URL at it, loads .env, and starts the real server in-process
 * with all configured integrations (Nylon Pay, Firebase, NVIDIA AI) active.
 */

import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import EmbeddedPostgres from "embedded-postgres";
import { loadEnvFile } from "./env.js";
import { startServer } from "./server.js";

async function main(): Promise<void> {
  loadEnvFile();

  const pgPort = 40000 + Math.floor(Math.random() * 5000);
  const dataDir = mkdtempSync(join(tmpdir(), "scottstechx-dev-pg-"));
  const pg = new EmbeddedPostgres({
    databaseDir: dataDir,
    user: "app",
    password: "app",
    port: pgPort,
    persistent: false,
  });

  await pg.initialise();
  await pg.start();
  await pg.createDatabase("scottstechx");

  process.env.DATABASE_URL = `postgres://app:app@127.0.0.1:${pgPort}/scottstechx`;
  console.log(`[dev] embedded Postgres on :${pgPort}`);

  // Keep the pg reference alive and tear it down cleanly on exit.
  const cleanup = async () => {
    try {
      await pg.stop();
    } catch {
      /* best effort */
    }
  };
  process.on("SIGINT", () => {
    void cleanup().then(() => process.exit(130));
  });
  process.on("SIGTERM", () => {
    void cleanup().then(() => process.exit(143));
  });

  await startServer();
  console.log("[dev] server ready — Ctrl+C to stop");
}

main().catch((err) => {
  console.error("[dev] failed:", err);
  process.exit(1);
});
