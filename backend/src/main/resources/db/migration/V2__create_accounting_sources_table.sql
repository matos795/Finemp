CREATE TABLE accounting_sources (
    id UUID NOT NULL,
    company_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    external_reference VARCHAR(255),
    active BOOLEAN NOT NULL,

    CONSTRAINT pk_accounting_sources
        PRIMARY KEY (id),

    CONSTRAINT fk_accounting_sources_company
        FOREIGN KEY (company_id)
        REFERENCES companies (id)
);