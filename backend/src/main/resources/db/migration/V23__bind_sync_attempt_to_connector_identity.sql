ALTER TABLE sync_attempts ADD COLUMN connector_credential_id UUID REFERENCES connector_credentials(id);
ALTER TABLE sync_attempts ADD COLUMN connector_credential_version BIGINT;
ALTER TABLE sync_attempts ADD CONSTRAINT ck_sync_attempt_agent_binding CHECK (
    (connector_credential_id IS NULL AND connector_credential_version IS NULL) OR
    (connector_credential_id IS NOT NULL AND connector_credential_version IS NOT NULL AND connector_credential_version >= 0)
);
CREATE INDEX ix_sync_attempts_connector ON sync_attempts(connector_credential_id) WHERE connector_credential_id IS NOT NULL;
