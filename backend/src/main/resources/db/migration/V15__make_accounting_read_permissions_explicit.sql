-- These grants could already read the complete ledger and classification list.
-- Encode only those pre-existing read capabilities before switching the guards.
-- No MANAGE right is granted. RESTRICTED remains denied by the scope guard.
INSERT INTO company_access_grant_permissions (company_access_grant_id, permission)
SELECT id, 'ACCOUNTING_MOVEMENT_VIEW' FROM company_access_grants
ON CONFLICT DO NOTHING;

INSERT INTO company_access_grant_permissions (company_access_grant_id, permission)
SELECT id, 'ACCOUNT_CLASSIFICATION_VIEW' FROM company_access_grants
ON CONFLICT DO NOTHING;
