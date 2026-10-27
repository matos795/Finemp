-- No automatic cleanup: inconsistent legacy identities require explicit review.
-- Locks prevent a concurrent legacy writer from invalidating the preflight.
LOCK TABLE accounting_accounts IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE accounting_movements IN SHARE ROW EXCLUSIVE MODE;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM accounting_accounts GROUP BY accounting_source_id, external_account_id HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM accounting_movements movement JOIN accounting_accounts account ON account.id = movement.account_id
                  GROUP BY account.accounting_source_id, movement.source_entry_key, movement.side HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'Legacy accounting identities require explicit reconciliation before migration';
    END IF;
END $$;

ALTER TABLE accounting_accounts ADD CONSTRAINT uk_account_source_external_id UNIQUE(accounting_source_id, external_account_id);
ALTER TABLE accounting_accounts ADD CONSTRAINT uk_account_id_source UNIQUE(id, accounting_source_id);
ALTER TABLE accounting_movements ADD COLUMN accounting_source_id UUID;
UPDATE accounting_movements movement SET accounting_source_id = account.accounting_source_id
    FROM accounting_accounts account WHERE account.id = movement.account_id;

-- Backward-compatible default for older writers; explicit wrong ownership is still rejected by the FK.
CREATE FUNCTION finemp_movement_source_default() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.accounting_source_id IS NULL THEN
        SELECT account.accounting_source_id INTO NEW.accounting_source_id
            FROM accounting_accounts account WHERE account.id = NEW.account_id;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tr_movement_source_default BEFORE INSERT OR UPDATE OF account_id, accounting_source_id
    ON accounting_movements FOR EACH ROW EXECUTE FUNCTION finemp_movement_source_default();

ALTER TABLE accounting_movements ALTER COLUMN accounting_source_id SET NOT NULL;
ALTER TABLE accounting_movements ADD CONSTRAINT fk_movement_account_source
    FOREIGN KEY(account_id, accounting_source_id) REFERENCES accounting_accounts(id, accounting_source_id);
ALTER TABLE accounting_movements ADD CONSTRAINT uk_movement_source_entry_side
    UNIQUE(accounting_source_id, source_entry_key, side);

ALTER TABLE sync_runs DROP CONSTRAINT sync_runs_failure_code_check;
ALTER TABLE sync_runs ADD CONSTRAINT sync_runs_failure_code_check CHECK (
    failure_code IN ('READ_FAILED', 'IDENTITY_MISMATCH', 'INVALID_PAYLOAD', 'PUBLICATION_FAILED',
        'LEASE_EXPIRED', 'RETRY_LIMIT', 'SOURCE_NOT_AVAILABLE', 'ACCESS_REVOKED', 'UNSUPPORTED_MODE', 'DATA_CONFLICT', 'RESOURCE_LIMIT')
);
