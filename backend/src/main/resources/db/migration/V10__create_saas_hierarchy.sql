CREATE TABLE accounting_firms (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,

    CONSTRAINT pk_accounting_firms PRIMARY KEY (id)
);

CREATE TABLE clients (
    id UUID NOT NULL,
    accounting_firm_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,

    CONSTRAINT pk_clients PRIMARY KEY (id),

    CONSTRAINT fk_clients_accounting_firm
        FOREIGN KEY (accounting_firm_id)
        REFERENCES accounting_firms (id)
);

CREATE INDEX ix_clients_accounting_firm_id
    ON clients (accounting_firm_id);

ALTER TABLE companies
    ADD COLUMN client_id UUID;

ALTER TABLE companies
    ADD CONSTRAINT fk_companies_client
        FOREIGN KEY (client_id)
        REFERENCES clients (id);

CREATE INDEX ix_companies_client_id
    ON companies (client_id);

