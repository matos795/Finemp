CREATE TABLE sync_run_events (
    id UUID PRIMARY KEY,
    sync_run_id UUID NOT NULL REFERENCES sync_runs(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('REQUESTED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    actor_type VARCHAR(20) NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM', 'CONNECTOR')),
    actor_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((actor_type = 'SYSTEM' AND actor_id IS NULL) OR (actor_type <> 'SYSTEM' AND actor_id IS NOT NULL))
);
CREATE INDEX ix_sync_run_events_run_occurred ON sync_run_events(sync_run_id, occurred_at DESC, id);

