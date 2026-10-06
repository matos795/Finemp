CREATE TABLE accounting_accounts (
    id UUID NOT NULL,
    accounting_source_id UUID NOT NULL,
    external_account_id VARCHAR(255) NOT NULL,
    account_key VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,

    CONSTRAINT pk_accounting_accounts
        PRIMARY KEY (id),

    CONSTRAINT fk_accounting_accounts_source
        FOREIGN KEY (accounting_source_id)
        REFERENCES accounting_sources (id)
);