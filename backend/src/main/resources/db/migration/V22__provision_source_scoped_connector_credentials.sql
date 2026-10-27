ALTER TABLE accounting_sources ADD COLUMN connection_mode VARCHAR(20) NOT NULL DEFAULT 'DIRECT'
    CHECK (connection_mode IN ('DIRECT', 'CONNECTOR'));

CREATE TABLE connector_credentials (
    id UUID PRIMARY KEY,
    accounting_firm_id UUID NOT NULL REFERENCES accounting_firms(id),
    accounting_source_id UUID NOT NULL REFERENCES accounting_sources(id),
    label VARCHAR(80) NOT NULL CHECK (length(trim(label)) > 0),
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[a-f0-9]{64}$'),
    issued_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL CHECK (expires_at > created_at),
    revoked_at TIMESTAMP WITH TIME ZONE CHECK (revoked_at >= created_at),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_connector_credentials_one_active_source ON connector_credentials(accounting_source_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_connector_credentials_firm_created ON connector_credentials(accounting_firm_id, created_at DESC, id);

CREATE TABLE connector_credential_events (
    id UUID PRIMARY KEY,
    credential_id UUID NOT NULL REFERENCES connector_credentials(id),
    action VARCHAR(20) NOT NULL CHECK (action IN ('ISSUED', 'ROTATED', 'REVOKED')),
    actor_user_id UUID NOT NULL REFERENCES user_accounts(id),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_connector_credential_events_history ON connector_credential_events(credential_id, occurred_at, id);
