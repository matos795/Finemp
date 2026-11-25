CREATE TABLE accounting_account_source_attributes (
    id UUID NOT NULL,
    accounting_source_id UUID NOT NULL,
    source_account_id VARCHAR(255) NOT NULL,
    classification_code VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    account_type VARCHAR(255) NOT NULL,
    calculation_type VARCHAR(255) NOT NULL,
    balance_nature VARCHAR(255) NOT NULL,
    balance_control VARCHAR(255) NOT NULL,
    show_in_balance_sheet VARCHAR(255) NOT NULL,
    native_dre_code VARCHAR(255),
    native_dre_account VARCHAR(255),

    CONSTRAINT pk_accounting_account_source_attributes PRIMARY KEY (id),
    CONSTRAINT uk_account_source_attributes_identity
        UNIQUE (accounting_source_id, source_account_id),
    CONSTRAINT fk_account_source_attributes_source
        FOREIGN KEY (accounting_source_id)
        REFERENCES accounting_sources (id),
    CONSTRAINT fk_account_source_attributes_account
        FOREIGN KEY (accounting_source_id, source_account_id)
        REFERENCES accounting_accounts (accounting_source_id, external_account_id)
);

CREATE TABLE accounting_entry_source_metadata (
    id UUID NOT NULL,
    accounting_source_id UUID NOT NULL,
    source_entry_key VARCHAR(255) NOT NULL,
    source_recorded_at TIMESTAMP WITH TIME ZONE,
    history_code VARCHAR(255),
    origin_code VARCHAR(255),
    entry_type_code VARCHAR(255),
    data_origin_code VARCHAR(255),
    batch_code VARCHAR(255),
    document_number VARCHAR(255),
    operation_group_key VARCHAR(255),
    transaction_key VARCHAR(255),
    origin_establishment_code VARCHAR(255),

    CONSTRAINT pk_accounting_entry_source_metadata PRIMARY KEY (id),
    CONSTRAINT uk_entry_source_metadata_identity
        UNIQUE (accounting_source_id, source_entry_key),
    CONSTRAINT fk_entry_source_metadata_source
        FOREIGN KEY (accounting_source_id)
        REFERENCES accounting_sources (id)
);

CREATE TABLE accounting_daily_debit_credit_totals (
    id UUID NOT NULL,
    accounting_source_id UUID NOT NULL,
    establishment_code VARCHAR(255) NOT NULL,
    source_account_id VARCHAR(255) NOT NULL,
    day DATE NOT NULL,
    entry_type_code VARCHAR(255) NOT NULL,
    debit_total NUMERIC(19,4) NOT NULL,
    credit_total NUMERIC(19,4) NOT NULL,

    CONSTRAINT pk_accounting_daily_debit_credit_totals PRIMARY KEY (id),
    CONSTRAINT uk_daily_debit_credit_totals_identity
        UNIQUE (accounting_source_id, establishment_code, source_account_id, day, entry_type_code),
    CONSTRAINT fk_daily_debit_credit_totals_source
        FOREIGN KEY (accounting_source_id)
        REFERENCES accounting_sources (id),
    CONSTRAINT fk_daily_debit_credit_totals_account
        FOREIGN KEY (accounting_source_id, source_account_id)
        REFERENCES accounting_accounts (accounting_source_id, external_account_id),
    CONSTRAINT ck_daily_debit_credit_totals_non_negative
        CHECK (debit_total >= 0 AND credit_total >= 0)
);

CREATE TABLE accounting_monthly_debit_credit_totals (
    id UUID NOT NULL,
    accounting_source_id UUID NOT NULL,
    establishment_code VARCHAR(255) NOT NULL,
    source_account_id VARCHAR(255) NOT NULL,
    month_start DATE NOT NULL,
    entry_type_code VARCHAR(255) NOT NULL,
    debit_total NUMERIC(19,4) NOT NULL,
    credit_total NUMERIC(19,4) NOT NULL,

    CONSTRAINT pk_accounting_monthly_debit_credit_totals PRIMARY KEY (id),
    CONSTRAINT uk_monthly_debit_credit_totals_identity
        UNIQUE (accounting_source_id, establishment_code, source_account_id, month_start, entry_type_code),
    CONSTRAINT fk_monthly_debit_credit_totals_source
        FOREIGN KEY (accounting_source_id)
        REFERENCES accounting_sources (id),
    CONSTRAINT fk_monthly_debit_credit_totals_account
        FOREIGN KEY (accounting_source_id, source_account_id)
        REFERENCES accounting_accounts (accounting_source_id, external_account_id),
    CONSTRAINT ck_monthly_debit_credit_totals_non_negative
        CHECK (debit_total >= 0 AND credit_total >= 0),
    CONSTRAINT ck_monthly_debit_credit_totals_month_start
        CHECK (EXTRACT(DAY FROM month_start) = 1)
);

CREATE TABLE accounting_origin_catalog (
    id UUID NOT NULL,
    accounting_source_id UUID NOT NULL,
    origin_code VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,

    CONSTRAINT pk_accounting_origin_catalog PRIMARY KEY (id),
    CONSTRAINT uk_accounting_origin_catalog_identity
        UNIQUE (accounting_source_id, origin_code),
    CONSTRAINT fk_accounting_origin_catalog_source
        FOREIGN KEY (accounting_source_id)
        REFERENCES accounting_sources (id)
);
