CREATE TABLE accounting_dataset_coverage (
    id UUID NOT NULL,
    accounting_source_id UUID NOT NULL,
    dataset VARCHAR(40) NOT NULL,
    imported_from DATE NOT NULL,
    imported_to DATE NOT NULL,

    CONSTRAINT pk_accounting_dataset_coverage PRIMARY KEY (id),
    CONSTRAINT fk_accounting_dataset_coverage_source
        FOREIGN KEY (accounting_source_id)
        REFERENCES accounting_sources(id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_accounting_dataset_coverage_dataset
        CHECK (dataset IN (
            'CHART',
            'MOVEMENTS',
            'DAILY_TOTALS',
            'MONTHLY_TOTALS',
            'ORIGIN_CATALOG',
            'PARTICIPANTS',
            'COST_ALLOCATIONS'
        )),
    CONSTRAINT ck_accounting_dataset_coverage_interval
        CHECK (imported_to >= imported_from),
    CONSTRAINT uk_accounting_dataset_coverage_interval
        UNIQUE (accounting_source_id, dataset, imported_from, imported_to)
);

CREATE INDEX idx_accounting_dataset_coverage_lookup
    ON accounting_dataset_coverage(
        accounting_source_id,
        dataset,
        imported_from,
        imported_to
    );

WITH succeeded AS (
    SELECT
        accounting_source_id,
        start_date AS imported_from,
        end_date AS imported_to
    FROM sync_runs
    WHERE status = 'SUCCEEDED'
),
ordered AS (
    SELECT
        accounting_source_id,
        imported_from,
        imported_to,
        MAX(imported_to) OVER (
            PARTITION BY accounting_source_id
            ORDER BY imported_from, imported_to
            ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
        ) AS previous_max_to
    FROM succeeded
),
marked AS (
    SELECT
        accounting_source_id,
        imported_from,
        imported_to,
        CASE
            WHEN previous_max_to IS NULL
                OR imported_from > previous_max_to + 1
            THEN 1
            ELSE 0
        END AS starts_island
    FROM ordered
),
grouped AS (
    SELECT
        accounting_source_id,
        imported_from,
        imported_to,
        SUM(starts_island) OVER (
            PARTITION BY accounting_source_id
            ORDER BY imported_from, imported_to
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS island
    FROM marked
),
merged AS (
    SELECT
        accounting_source_id,
        MIN(imported_from) AS imported_from,
        MAX(imported_to) AS imported_to
    FROM grouped
    GROUP BY accounting_source_id, island
)
INSERT INTO accounting_dataset_coverage(
    id,
    accounting_source_id,
    dataset,
    imported_from,
    imported_to
)
SELECT
    gen_random_uuid(),
    accounting_source_id,
    'MOVEMENTS',
    imported_from,
    imported_to
FROM merged;
