ALTER TABLE sync_runs ADD COLUMN attempt_number INTEGER NOT NULL DEFAULT 0 CHECK (attempt_number BETWEEN 0 AND 10);

CREATE TABLE sync_attempts (
    id UUID PRIMARY KEY,
    sync_run_id UUID NOT NULL REFERENCES sync_runs(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL CHECK (attempt_number BETWEEN 1 AND 10),
    fence_version BIGINT NOT NULL CHECK (fence_version >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_renewed_at TIMESTAMP WITH TIME ZONE NOT NULL CHECK (last_renewed_at >= started_at),
    lease_expires_at TIMESTAMP WITH TIME ZONE NOT NULL CHECK (lease_expires_at >= last_renewed_at),
    finished_at TIMESTAMP WITH TIME ZONE CHECK (finished_at >= started_at),
    next_attempt_at TIMESTAMP WITH TIME ZONE CHECK (next_attempt_at >= finished_at),
    failure_code VARCHAR(40) CHECK (failure_code IN ('READ_FAILED', 'IDENTITY_MISMATCH', 'INVALID_PAYLOAD',
        'PUBLICATION_FAILED', 'LEASE_EXPIRED', 'RETRY_LIMIT', 'SOURCE_NOT_AVAILABLE', 'ACCESS_REVOKED',
        'UNSUPPORTED_MODE', 'DATA_CONFLICT', 'RESOURCE_LIMIT')),
    UNIQUE (sync_run_id, attempt_number),
    UNIQUE (sync_run_id, fence_version),
    CHECK ((status = 'RUNNING' AND finished_at IS NULL AND failure_code IS NULL AND next_attempt_at IS NULL)
        OR (status = 'SUCCEEDED' AND finished_at IS NOT NULL AND failure_code IS NULL AND next_attempt_at IS NULL)
        OR (status = 'FAILED' AND finished_at IS NOT NULL AND failure_code IS NOT NULL)
        OR (status = 'EXPIRED' AND finished_at IS NOT NULL AND failure_code = 'LEASE_EXPIRED'))
);
CREATE UNIQUE INDEX ux_sync_attempts_one_running ON sync_attempts(sync_run_id) WHERE status = 'RUNNING';
CREATE INDEX ix_sync_attempts_retry_due ON sync_attempts(next_attempt_at) WHERE next_attempt_at IS NOT NULL;
CREATE INDEX ix_sync_attempts_lease_due ON sync_attempts(lease_expires_at) WHERE status = 'RUNNING';

-- Fence pre-upgrade workers. Keep prior runs/data/events; active work becomes reclaimable, never silently successful.
UPDATE sync_runs SET attempt_number = 1, version = version + 1 WHERE status = 'RUNNING';
INSERT INTO sync_attempts (id, sync_run_id, attempt_number, fence_version, status, started_at, last_renewed_at, lease_expires_at)
SELECT gen_random_uuid(), id, 1, version, 'RUNNING', started_at, started_at, GREATEST(started_at, CURRENT_TIMESTAMP)
FROM sync_runs WHERE status = 'RUNNING';
