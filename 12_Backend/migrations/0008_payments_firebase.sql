-- 0008_payments_firebase.sql
-- Payments ledger + Firebase Auth linkage.
--
-- 1. `payments` — one row per payment attempt, keyed by the provider's
--    reference (Nylon Pay `reference` is a UUID and is idempotent).
-- 2. `users.firebase_uid` — maps a Firebase Auth `uid` to a local user so
--    the Firebase sign-in/signup flow can upsert into the same users table
--    used by phone+password login.

CREATE TABLE IF NOT EXISTS payments (
  id              uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  order_id        uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  provider        text NOT NULL,
  reference       text UNIQUE NOT NULL,
  amount_minor    bigint NOT NULL CHECK (amount_minor >= 0),
  currency        char(3) NOT NULL,
  status          text NOT NULL DEFAULT 'processing',
  provider_status text,
  metadata        jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_payments_order ON payments(order_id);

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS firebase_uid text;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_firebase_uid
  ON users(firebase_uid)
  WHERE firebase_uid IS NOT NULL;

-- =========================================================
-- RLS for payments (mirrors the orders policies)
-- =========================================================
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS payments_select ON payments;
CREATE POLICY payments_select ON payments
  FOR SELECT USING (
    current_setting('app.user_role', true) = 'admin'
    OR EXISTS (
      SELECT 1 FROM orders o
      WHERE o.id = payments.order_id
        AND (
          o.customer_id = current_setting('app.user_id', true)::uuid
          OR o.seller_id = current_setting('app.user_id', true)::uuid
          OR o.assigned_driver_id = current_setting('app.user_id', true)::uuid
        )
    )
  );

DROP POLICY IF EXISTS payments_insert ON payments;
CREATE POLICY payments_insert ON payments
  FOR INSERT WITH CHECK (
    current_setting('app.user_role', true) = 'admin'
    OR EXISTS (
      SELECT 1 FROM orders o
      WHERE o.id = payments.order_id
        AND o.customer_id = current_setting('app.user_id', true)::uuid
    )
  );

DROP POLICY IF EXISTS payments_update ON payments;
CREATE POLICY payments_update ON payments
  FOR UPDATE USING (
    current_setting('app.user_role', true) = 'admin'
    OR EXISTS (
      SELECT 1 FROM orders o
      WHERE o.id = payments.order_id
        AND o.customer_id = current_setting('app.user_id', true)::uuid
    )
  );
