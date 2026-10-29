CREATE TABLE financial_statement_action_receipts (
    company_id UUID NOT NULL REFERENCES companies(id),
    actor_id UUID NOT NULL REFERENCES user_accounts(id),
    command_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
    result_state TEXT NOT NULL CHECK(jsonb_typeof(result_state::jsonb)='object'),
    committed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(company_id,actor_id,command_id)
);
ALTER TABLE access_audit_events ADD CONSTRAINT ck_statement_ignore_audit_context CHECK(
    action <> 'FINANCIAL_STATEMENT_IGNORED' OR
    (resource_type='FINANCIAL_STATEMENT_ITEM' AND resource_id IS NOT NULL AND command_id IS NOT NULL
     AND company_id IS NOT NULL AND actor_user_account_id IS NOT NULL AND before_state IS NOT NULL AND after_state IS NOT NULL) IS TRUE
);
