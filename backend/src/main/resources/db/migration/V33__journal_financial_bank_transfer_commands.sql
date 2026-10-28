CREATE TABLE financial_bank_transfer_command_receipts (
    company_id UUID NOT NULL REFERENCES companies(id),
    actor_id UUID NOT NULL REFERENCES user_accounts(id),
    command_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
    transfer_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL CHECK(action IN ('CREATED','DELETED')),
    result_version BIGINT NOT NULL CHECK(result_version>=0),
    committed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(company_id,actor_id,command_id)
);
CREATE TABLE financial_bank_transfer_events (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    actor_id UUID NOT NULL REFERENCES user_accounts(id),
    command_id UUID NOT NULL,
    transfer_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL CHECK(action IN ('CREATED','DELETED')),
    before_version BIGINT CHECK(before_version>=0),
    after_version BIGINT CHECK(after_version>=0),
    before_state TEXT CHECK(before_state IS NULL OR jsonb_typeof(before_state::jsonb)='object'),
    after_state TEXT CHECK(after_state IS NULL OR jsonb_typeof(after_state::jsonb)='object'),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_bank_transfer_event_command UNIQUE(company_id,actor_id,command_id),
    CONSTRAINT ck_bank_transfer_event_transition CHECK(
        (action='CREATED' AND before_version IS NULL AND before_state IS NULL AND after_version IS NOT NULL AND after_version=0 AND after_state IS NOT NULL)
        OR (action='DELETED' AND before_version IS NOT NULL AND before_state IS NOT NULL AND after_version IS NULL AND after_state IS NULL))
);
CREATE INDEX idx_bank_transfer_events_history ON financial_bank_transfer_events(company_id,transfer_id,occurred_at,id);
-- No transfer FK: receipts and before-state remain available after deletion.
