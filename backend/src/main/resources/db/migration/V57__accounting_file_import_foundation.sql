ALTER TABLE accounting_sources
    DROP CONSTRAINT accounting_sources_connection_mode_check;

ALTER TABLE accounting_sources
    ADD CONSTRAINT accounting_sources_connection_mode_check
    CHECK (connection_mode IN ('DIRECT', 'CONNECTOR', 'UPLOAD'));

CREATE TABLE accounting_file_import_receipts (
    accounting_source_id UUID NOT NULL,
    idempotency_key UUID NOT NULL,
    dataset VARCHAR(40) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    layout_profile VARCHAR(80) NOT NULL,
    covered_from DATE NOT NULL,
    covered_to DATE NOT NULL,
    row_count BIGINT NOT NULL,
    published_count BIGINT NOT NULL,
    skipped_count BIGINT NOT NULL,
    created_by UUID NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_accounting_file_import_receipts
        PRIMARY KEY (accounting_source_id, idempotency_key),

    CONSTRAINT fk_accounting_file_import_receipts_source
        FOREIGN KEY (accounting_source_id)
        REFERENCES accounting_sources(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_accounting_file_import_receipts_actor
        FOREIGN KEY (created_by)
        REFERENCES user_accounts(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_accounting_file_import_receipts_file_hash
        CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_accounting_file_import_receipts_fingerprint
        CHECK (fingerprint ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_accounting_file_import_receipts_layout
        CHECK (length(trim(layout_profile)) BETWEEN 1 AND 80),

    CONSTRAINT ck_accounting_file_import_receipts_period
        CHECK (covered_from <= covered_to),

    CONSTRAINT ck_accounting_file_import_receipts_counts
        CHECK (
            row_count >= 0
            AND published_count >= 0
            AND skipped_count >= 0
            AND published_count <= row_count
            AND skipped_count <= row_count - published_count
        )
);

CREATE INDEX idx_accounting_file_import_receipts_committed
    ON accounting_file_import_receipts(accounting_source_id, committed_at DESC);
