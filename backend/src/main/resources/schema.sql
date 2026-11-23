CREATE TABLE IF NOT EXISTS pods (
  id UUID PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  ssh_host VARCHAR(255) NOT NULL,
  ssh_port INTEGER NOT NULL CHECK (ssh_port BETWEEN 1 AND 65535),
  ssh_username VARCHAR(80) NOT NULL,
  credential_type VARCHAR(20) NOT NULL,
  secret_ciphertext TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS pod_addresses (
  pod_id UUID NOT NULL REFERENCES pods(id) ON DELETE CASCADE,
  address VARCHAR(60) NOT NULL,
  role VARCHAR(20) NOT NULL,
  PRIMARY KEY (pod_id, address)
);
CREATE TABLE IF NOT EXISTS audit_events (
  id UUID PRIMARY KEY,
  at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  event VARCHAR(80) NOT NULL,
  pod_id UUID,
  details VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS audit_events_at_idx ON audit_events(at DESC);

-- A login session has an opaque random cookie; only its SHA-256 hash is stored
-- here, so browser logins survive backend restarts without persisting admin tokens.
CREATE TABLE IF NOT EXISTS admin_sessions (
  id_hash VARCHAR(64) PRIMARY KEY,
  csrf_token VARCHAR(64) NOT NULL,
  admin_fingerprint VARCHAR(64) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS admin_sessions_expires_idx ON admin_sessions(expires_at);
