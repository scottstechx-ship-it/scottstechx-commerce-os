/**
 * Password hashing for phone+password login.
 *
 * Uses Node's built-in scrypt (no native dependency) with a self-describing
 * storage format:  scrypt$N$r$p$<saltHex>$<hashHex>
 *
 * Storing N/r/p next to the hash means we can raise the work factor later
 * without invalidating existing passwords — verifyPassword reads the params
 * back out of the stored string.
 */

import { randomBytes, scryptSync, timingSafeEqual } from "node:crypto";

const DEFAULT_N = 16384; // 2^14 — interactive login cost
const DEFAULT_R = 8;
const DEFAULT_P = 1;
const KEYLEN = 64;
const SALT_BYTES = 16;

export function hashPassword(password: string): string {
  const salt = randomBytes(SALT_BYTES);
  const hash = scryptSync(password, salt, KEYLEN, {
    N: DEFAULT_N,
    r: DEFAULT_R,
    p: DEFAULT_P,
  });
  return `scrypt$${DEFAULT_N}$${DEFAULT_R}$${DEFAULT_P}$${salt.toString("hex")}$${hash.toString("hex")}`;
}

export function verifyPassword(password: string, stored: string): boolean {
  const parts = stored.split("$");
  if (parts.length !== 6 || parts[0] !== "scrypt") return false;
  const [, nStr, rStr, pStr, saltHex, hashHex] = parts as [string, string, string, string, string, string];
  const n = Number(nStr);
  const r = Number(rStr);
  const p = Number(pStr);
  if (!Number.isInteger(n) || !Number.isInteger(r) || !Number.isInteger(p)) return false;
  const salt = Buffer.from(saltHex, "hex");
  const expected = Buffer.from(hashHex, "hex");
  if (salt.length === 0 || expected.length === 0) return false;
  const actual = scryptSync(password, salt, expected.length, { N: n, r, p });
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}
