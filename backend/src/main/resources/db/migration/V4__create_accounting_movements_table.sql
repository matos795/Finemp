CREATE TABLE accounting_movements (
    id UUID NOT NULL,
    account_id UUID NOT NULL,
    source_entry_key VARCHAR(255) NOT NULL,
    side VARCHAR(10) NOT NULL,
    accounting_date DATE NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    history VARCHAR(255),

    CONSTRAINT pk_accounting_movements
        PRIMARY KEY (id),

    CONSTRAINT fk_accounting_movements_account
        FOREIGN KEY (account_id)
        REFERENCES accounting_accounts (id)
);