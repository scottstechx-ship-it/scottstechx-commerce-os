/**
 * Minimal dependency-free .env loader (Node 22, no `dotenv` package).
 *
 * Loads KEY=VALUE lines from `.env` in the backend directory into
 * process.env, but never overwrites a variable that is already set (so
 * explicit environment, spawn env, and tests always win). Handles:
 *   - `#` comments and blank lines
 *   - surrounding single/double quotes on the value
 *   - JSON values whose embedded newlines are escaped as \n (e.g. the
 *     Firebase service-account credential)
 *
 * Call loadEnvFile() once at process startup in the entrypoints that should
 * honor a local .env (server, dev, smoke). The test suite does NOT load it,
 * so tests stay deterministic.
 */

import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

export function loadEnvFile(file = ".env"): void {
  const path = resolve(file);
  if (!existsSync(path)) return;
  const text = readFileSync(path, "utf8");
  for (const raw of text.split("\n")) {
    const line = raw.trim();
    if (line === "" || line.startsWith("#")) continue;
    const eq = line.indexOf("=");
    if (eq < 0) continue;
    const key = line.slice(0, eq).trim();
    if (key === "") continue;
    let value = line.slice(eq + 1).trim();
    if (
      value.length >= 2 &&
      ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'")))
    ) {
      value = value.slice(1, -1);
    }
    if (process.env[key] === undefined) {
      process.env[key] = value;
    }
  }
}
