-- 0007_auth_login.sql
-- Phone + password login, plus POD signature storage.
--
-- 1. Adds a nullable, unique `phone` column to users for phone+password login.
--    Email stays the canonical unique identifier; phone is additive.
-- 2. Adds `pod_signature` to orders so the Android client's proof-of-delivery
--    signature photo (signaturePngBase64) is persisted instead of dropped.
-- 3. Seeds real credentials for the three demo users from 0003_seed.sql.
--    Passwords are scrypt hashes of "demo1234" (format
--    scrypt$N$r$p$saltHex$hashHex, see src/modules/auth/password.ts).

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS phone text;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone
  ON users(phone)
  WHERE phone IS NOT NULL;

ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS pod_signature text;

-- Demo credentials (all share password "demo1234").
UPDATE users SET phone = '+256700000001',
  password_hash = 'scrypt$16384$8$1$00000000000000000000000000000001$32fdaea905394aac5af1b1bca7857b021f4233dd8c6e2137576a32ff870161d7835bbbe4222a846410b3dd4eec57cb9ef8b5227bc7bdc9be79f11892e03f0711'
  WHERE id = '11111111-1111-4111-8111-111111111111';

UPDATE users SET phone = '+256700000002',
  password_hash = 'scrypt$16384$8$1$00000000000000000000000000000002$67cd160f1f86e9bbcdbb43b7022ee59578572e48e42c736fd46577e895cd74c8315adb63b12ae16da9fc83506105cc450ea040390216e25562e9ce2143daefef'
  WHERE id = '22222222-2222-4222-8222-222222222222';

UPDATE users SET phone = '+256700000003',
  password_hash = 'scrypt$16384$8$1$00000000000000000000000000000003$c44ffbf0f680fd79458781dd562796162fc1f7a0dc6e0f5a8d50c5e23315a28b305b47c5503ae8af62bc2c26532fdb88c5e0988cd615945ef5d10f84270a8971'
  WHERE id = '33333333-3333-4333-8333-333333333333';
