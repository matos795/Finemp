CREATE TABLE service_invoice_drafts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    business_partner_id UUID NOT NULL REFERENCES business_partners(id),
    contract_id UUID REFERENCES contracts(id),
    competence DATE NOT NULL,
    service_text VARCHAR(4000) NOT NULL,
    gross_amount NUMERIC(19,2) NOT NULL,
    iss_rate NUMERIC(7,4) NOT NULL DEFAULT 0,
    iss_withheld BOOLEAN NOT NULL DEFAULT FALSE,
    ir_rate NUMERIC(7,4) NOT NULL DEFAULT 0,
    pis_rate NUMERIC(7,4) NOT NULL DEFAULT 0,
    cofins_rate NUMERIC(7,4) NOT NULL DEFAULT 0,
    csll_rate NUMERIC(7,4) NOT NULL DEFAULT 0,
    inss_rate NUMERIC(7,4) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    invoice_number VARCHAR(80),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_service_invoice_competence_first_day
        CHECK (EXTRACT(DAY FROM competence) = 1),
    CONSTRAINT ck_service_invoice_gross_amount
        CHECK (gross_amount >= 0),
    CONSTRAINT ck_service_invoice_rates
        CHECK (
            iss_rate BETWEEN 0 AND 100
            AND ir_rate BETWEEN 0 AND 100
            AND pis_rate BETWEEN 0 AND 100
            AND cofins_rate BETWEEN 0 AND 100
            AND csll_rate BETWEEN 0 AND 100
            AND inss_rate BETWEEN 0 AND 100
        ),
    CONSTRAINT ck_service_invoice_status
        CHECK (status IN ('DRAFT','ISSUED','CANCELED')),
    CONSTRAINT ck_service_invoice_number_by_status
        CHECK (
            (status = 'ISSUED' AND invoice_number IS NOT NULL AND length(trim(invoice_number)) > 0)
            OR (status <> 'ISSUED' AND invoice_number IS NULL)
        )
);

CREATE INDEX idx_service_invoice_company_competence_status
    ON service_invoice_drafts(company_id, competence, status);

CREATE INDEX idx_service_invoice_company_partner
    ON service_invoice_drafts(company_id, business_partner_id);

CREATE INDEX idx_service_invoice_company_contract
    ON service_invoice_drafts(company_id, contract_id);
