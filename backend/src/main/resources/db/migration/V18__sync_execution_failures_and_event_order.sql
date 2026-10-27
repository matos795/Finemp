ALTER TABLE sync_runs DROP CONSTRAINT sync_runs_failure_code_check;
ALTER TABLE sync_runs ADD CONSTRAINT sync_runs_failure_code_check CHECK (
    failure_code IN ('READ_FAILED', 'IDENTITY_MISMATCH', 'INVALID_PAYLOAD', 'PUBLICATION_FAILED',
        'LEASE_EXPIRED', 'RETRY_LIMIT', 'SOURCE_NOT_AVAILABLE', 'ACCESS_REVOKED', 'UNSUPPORTED_MODE')
);

-- Overall lifecycle phases stay ordered even when a single transaction uses the same UTC instant.
ALTER TABLE sync_run_events ADD COLUMN transition_index INTEGER;
UPDATE sync_run_events SET transition_index = CASE status WHEN 'REQUESTED' THEN 0 WHEN 'RUNNING' THEN 1 ELSE 2 END;
ALTER TABLE sync_run_events ALTER COLUMN transition_index SET NOT NULL;
ALTER TABLE sync_run_events ADD CONSTRAINT ck_sync_event_phase CHECK (
    (status = 'REQUESTED' AND transition_index = 0) OR (status = 'RUNNING' AND transition_index = 1)
    OR (status IN ('SUCCEEDED', 'FAILED', 'CANCELLED') AND transition_index = 2)
);
CREATE UNIQUE INDEX ux_sync_run_event_phase ON sync_run_events(sync_run_id, transition_index);
