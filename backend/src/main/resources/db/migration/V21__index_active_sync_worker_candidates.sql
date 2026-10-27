CREATE INDEX ix_sync_runs_worker_active ON sync_runs(requested_at, id) WHERE status IN ('REQUESTED', 'RUNNING');
