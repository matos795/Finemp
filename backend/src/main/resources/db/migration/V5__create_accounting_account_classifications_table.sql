CREATE TABLE accounting_account_classifications (
    id UUID NOT NULL,
    accounting_account_id UUID NOT NULL,
    dre_classification VARCHAR(255),
    dre_open_classification VARCHAR(255),
    ebitda_classification VARCHAR(255),

    CONSTRAINT pk_accounting_account_classifications
        PRIMARY KEY (id),

    CONSTRAINT fk_accounting_account_classifications_account
        FOREIGN KEY (accounting_account_id)
        REFERENCES accounting_accounts (id),

    CONSTRAINT uk_accounting_account_classifications_account
        UNIQUE (accounting_account_id)
);