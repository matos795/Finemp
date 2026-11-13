CREATE TABLE debt_agreements (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    business_partner_id UUID REFERENCES business_partners(id),
    origin VARCHAR(20) NOT NULL,
    description VARCHAR(255) NOT NULL,
    amortization_system VARCHAR(20) NOT NULL,
    principal_amount NUMERIC(19,2) NOT NULL,
    installment_count INTEGER NOT NULL,
    input_rate NUMERIC(30,24),
    input_rate_period VARCHAR(20) NOT NULL,
    fixed_installment_amount NUMERIC(19,2),
    first_due_date DATE NOT NULL,
    effective_monthly_rate NUMERIC(30,12) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_debt_origin
        CHECK (origin IN ('BANK','SUPPLIER','TAX','THIRD_PARTY')),

    CONSTRAINT ck_debt_amortization_system
        CHECK (amortization_system IN ('SAC','FIXED')),

    CONSTRAINT ck_debt_input_rate_period
        CHECK (input_rate_period IN ('MONTHLY','ANNUAL','IMPLIED')),

    CONSTRAINT ck_debt_principal
        CHECK (principal_amount > 0),

    CONSTRAINT ck_debt_installment_count
        CHECK (installment_count BETWEEN 1 AND 1200),

    CONSTRAINT ck_debt_effective_rate
        CHECK (effective_monthly_rate >= 0),

    CONSTRAINT ck_debt_partner_by_origin
        CHECK (
            (origin = 'TAX' AND business_partner_id IS NULL)
            OR
            (origin <> 'TAX' AND business_partner_id IS NOT NULL)
        ),

    CONSTRAINT ck_debt_system_rate_shape
        CHECK (
            (
                amortization_system = 'SAC'
                AND input_rate_period IN ('MONTHLY','ANNUAL')
                AND input_rate IS NOT NULL
                AND input_rate >= 0
                AND fixed_installment_amount IS NULL
            )
            OR
            (
                amortization_system = 'FIXED'
                AND input_rate_period = 'IMPLIED'
                AND input_rate IS NULL
                AND fixed_installment_amount IS NOT NULL
                AND fixed_installment_amount > 0
            )
        )
);

CREATE INDEX idx_debt_agreements_company_origin
    ON debt_agreements(company_id, origin);

CREATE INDEX idx_debt_agreements_company_partner
    ON debt_agreements(company_id, business_partner_id);

CREATE TABLE debt_installments (
    id UUID PRIMARY KEY,
    debt_agreement_id UUID NOT NULL REFERENCES debt_agreements(id) ON DELETE CASCADE,
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    amortization_amount NUMERIC(19,2) NOT NULL,
    interest_amount NUMERIC(19,2) NOT NULL,
    installment_amount NUMERIC(19,2) NOT NULL,
    balance_after NUMERIC(19,2) NOT NULL,

    CONSTRAINT uq_debt_installment_number
        UNIQUE (debt_agreement_id, installment_number),

    CONSTRAINT ck_debt_installment_number
        CHECK (installment_number >= 1),

    CONSTRAINT ck_debt_installment_amounts
        CHECK (
            amortization_amount >= 0
            AND interest_amount >= 0
            AND installment_amount >= 0
            AND balance_after >= 0
        )
);

CREATE INDEX idx_debt_installments_due_date
    ON debt_installments(due_date);

CREATE INDEX idx_debt_installments_agreement_due_date
    ON debt_installments(debt_agreement_id, due_date);
