ALTER TABLE access_audit_events
    ADD COLUMN resource_type VARCHAR(80),
    ADD COLUMN resource_id UUID,
    ADD COLUMN command_id UUID,
    ADD COLUMN before_state TEXT,
    ADD COLUMN after_state TEXT,
    ADD COLUMN reason VARCHAR(1000);
ALTER TABLE access_audit_events ADD CONSTRAINT ck_financial_period_audit_context CHECK(
    action NOT IN ('FINANCIAL_PERIOD_CLOSED','FINANCIAL_PERIOD_REOPENED') OR
    (resource_type='FINANCIAL_PERIOD' AND resource_id IS NOT NULL AND command_id IS NOT NULL
     AND company_id IS NOT NULL AND actor_user_account_id IS NOT NULL AND after_state IS NOT NULL
     AND (action<>'FINANCIAL_PERIOD_REOPENED' OR (before_state IS NOT NULL AND reason IS NOT NULL AND length(trim(reason))>0))) IS TRUE);
CREATE INDEX idx_access_audit_resource ON access_audit_events(company_id,resource_type,resource_id,occurred_at DESC,id);

CREATE TABLE financial_periods (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    period VARCHAR(7) NOT NULL CHECK(period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$' AND left(period,4)<>'0000'),
    closed BOOLEAN NOT NULL,
    version BIGINT NOT NULL CHECK(version>=0),
    updated_by UUID NOT NULL REFERENCES user_accounts(id),
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(company_id,period)
);
CREATE TABLE financial_period_command_receipts (
    company_id UUID NOT NULL REFERENCES companies(id),
    actor_id UUID NOT NULL REFERENCES user_accounts(id),
    command_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
    result_state TEXT NOT NULL CHECK(jsonb_typeof(result_state::jsonb)='object'),
    committed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(company_id,actor_id,command_id)
);
