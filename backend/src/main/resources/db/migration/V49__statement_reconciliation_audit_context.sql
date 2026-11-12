ALTER TABLE access_audit_events ADD CONSTRAINT ck_statement_reconciliation_audit_context CHECK(
    action <> 'FINANCIAL_STATEMENT_RECONCILED' OR
    (resource_type='FINANCIAL_STATEMENT_ITEM' AND resource_id IS NOT NULL AND command_id IS NOT NULL
     AND company_id IS NOT NULL AND actor_user_account_id IS NOT NULL AND before_state IS NOT NULL AND after_state IS NOT NULL) IS TRUE
);
