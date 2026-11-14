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
