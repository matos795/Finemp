ALTER TABLE accounting_dataset_coverage
    ADD COLUMN establishment_code VARCHAR(255),
    ADD COLUMN published_at TIMESTAMPTZ;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM accounting_dataset_coverage
        WHERE dataset IN ('CHART', 'ORIGIN_CATALOG')
    ) THEN
        RAISE EXCEPTION 'Snapshot dataset coverage exists in legacy interval shape';
    END IF;
END
$$;

ALTER TABLE accounting_dataset_coverage
    ALTER COLUMN imported_from DROP NOT NULL,
    ALTER COLUMN imported_to DROP NOT NULL;

ALTER TABLE accounting_dataset_coverage
    DROP CONSTRAINT ck_accounting_dataset_coverage_interval,
    DROP CONSTRAINT uk_accounting_dataset_coverage_interval;

ALTER TABLE accounting_dataset_coverage
    ADD CONSTRAINT ck_accounting_dataset_coverage_establishment
        CHECK (establishment_code IS NULL OR btrim(establishment_code) <> ''),
    ADD CONSTRAINT ck_accounting_dataset_coverage_shape
        CHECK (
            (
                dataset IN ('CHART', 'ORIGIN_CATALOG')
                AND imported_from IS NULL
                AND imported_to IS NULL
                AND published_at IS NOT NULL
            )
            OR
            (
                dataset NOT IN ('CHART', 'ORIGIN_CATALOG')
                AND imported_from IS NOT NULL
                AND imported_to IS NOT NULL
                AND imported_to >= imported_from
            )
        );

DROP INDEX idx_accounting_dataset_coverage_lookup;

CREATE INDEX idx_accounting_dataset_coverage_lookup
    ON accounting_dataset_coverage(
        accounting_source_id,
        dataset,
        establishment_code,
        imported_from,
        imported_to
    );

CREATE UNIQUE INDEX uk_accounting_dataset_coverage_interval_scope
    ON accounting_dataset_coverage(
        accounting_source_id,
        dataset,
        COALESCE(establishment_code, ''),
        imported_from,
        imported_to
    )
    WHERE imported_from IS NOT NULL;

CREATE UNIQUE INDEX uk_accounting_dataset_coverage_snapshot_scope
    ON accounting_dataset_coverage(
        accounting_source_id,
        dataset,
        COALESCE(establishment_code, '')
    )
    WHERE imported_from IS NULL AND imported_to IS NULL;
