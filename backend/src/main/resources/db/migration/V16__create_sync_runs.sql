CREATE TABLE sync_runs (
    id UUID PRIMARY KEY,
    accounting_source_id UUID NOT NULL REFERENCES accounting_sources(id),
    requested_by_user_id UUID NOT NULL,
    idempotency_key UUID NOT NULL,
    mode VARCHAR(30) NOT NULL CHECK (mode IN ('INITIAL', 'INCREMENTAL', 'RECONCILIATION')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('REQUESTED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL CHECK (end_date >= start_date),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    read_count BIGINT CHECK (read_count >= 0),
    imported_count BIGINT CHECK (imported_count >= 0),
    skipped_count BIGINT CHECK (skipped_count >= 0),
    failure_code VARCHAR(40) CHECK (failure_code IN ('READ_FAILED', 'IDENTITY_MISMATCH', 'INVALID_PAYLOAD', 'PUBLICATION_FAILED', 'LEASE_EXPIRED', 'RETRY_LIMIT')),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (accounting_source_id, idempotency_key),
    CHECK (started_at IS NULL OR started_at >= requested_at),
    CHECK (finished_at IS NULL OR finished_at >= requested_at),
    CHECK (started_at IS NULL OR finished_at IS NULL OR finished_at >= started_at),
    CHECK (
        (status = 'REQUESTED' AND started_at IS NULL AND finished_at IS NULL AND failure_code IS NULL) OR
        (status = 'RUNNING' AND started_at IS NOT NULL AND finished_at IS NULL AND failure_code IS NULL) OR
        (status = 'SUCCEEDED' AND started_at IS NOT NULL AND finished_at IS NOT NULL AND failure_code IS NULL) OR
        (status = 'FAILED' AND started_at IS NOT NULL AND finished_at IS NOT NULL AND failure_code IS NOT NULL) OR
        (status = 'CANCELLED' AND started_at IS NULL AND finished_at IS NOT NULL AND failure_code IS NULL)
    ),
    CHECK (
        (status = 'SUCCEEDED' AND read_count IS NOT NULL AND imported_count IS NOT NULL AND skipped_count IS NOT NULL
             AND read_count::NUMERIC = imported_count::NUMERIC + skipped_count::NUMERIC) OR
        (status <> 'SUCCEEDED' AND read_count IS NULL AND imported_count IS NULL AND skipped_count IS NULL)
    )
);

CREATE UNIQUE INDEX ux_sync_runs_one_active_source ON sync_runs(accounting_source_id)
    WHERE status IN ('REQUESTED', 'RUNNING');
CREATE INDEX ix_sync_runs_source_requested ON sync_runs(accounting_source_id, requested_at DESC, id);
