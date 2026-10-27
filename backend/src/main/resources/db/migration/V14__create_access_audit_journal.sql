CREATE TABLE access_audit_events (
    id UUID PRIMARY KEY,
    action VARCHAR(80) NOT NULL,
    actor_user_account_id UUID,
    subject_user_account_id UUID NOT NULL,
    accounting_firm_id UUID,
    company_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_access_audit_subject_time
    ON access_audit_events (subject_user_account_id, occurred_at DESC, id);
CREATE INDEX ix_access_audit_firm_time
    ON access_audit_events (accounting_firm_id, occurred_at DESC, id);
CREATE INDEX ix_access_audit_company_time
    ON access_audit_events (company_id, occurred_at DESC, id);

CREATE TABLE access_audit_event_permissions (
    event_id UUID NOT NULL REFERENCES access_audit_events (id) ON DELETE CASCADE,
    permission VARCHAR(80) NOT NULL,
    PRIMARY KEY (event_id, permission)
);
