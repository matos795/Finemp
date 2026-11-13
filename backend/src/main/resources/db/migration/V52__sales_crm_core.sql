CREATE TABLE sales_opportunities (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    business_partner_id UUID REFERENCES business_partners(id),
    title VARCHAR(180) NOT NULL,
    stage VARCHAR(80) NOT NULL,
    monthly_amount NUMERIC(19,2) NOT NULL,
    probability INTEGER NOT NULL,
    responsible VARCHAR(160) NOT NULL,
    expected_close_on DATE,
    notes TEXT,
    status VARCHAR(20) NOT NULL,
    won_at TIMESTAMPTZ,
    lost_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sales_opportunity_amount CHECK (monthly_amount >= 0),
    CONSTRAINT ck_sales_opportunity_probability CHECK (probability BETWEEN 0 AND 100),
    CONSTRAINT ck_sales_opportunity_status CHECK (status IN ('OPEN','WON','LOST'))
);

CREATE INDEX idx_sales_opportunity_company_stage
    ON sales_opportunities(company_id, status, stage, id);

CREATE INDEX idx_sales_opportunity_company_partner
    ON sales_opportunities(company_id, business_partner_id, id);

CREATE INDEX idx_sales_opportunity_expected_close
    ON sales_opportunities(company_id, expected_close_on, status);

CREATE TABLE opportunity_stage_history (
    id UUID PRIMARY KEY,
    opportunity_id UUID NOT NULL REFERENCES sales_opportunities(id) ON DELETE CASCADE,
    stage VARCHAR(80) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_opportunity_stage_history_opportunity
    ON opportunity_stage_history(opportunity_id, recorded_at, id);

CREATE TABLE sales_activities (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    opportunity_id UUID REFERENCES sales_opportunities(id),
    business_partner_id UUID REFERENCES business_partners(id),
    subject VARCHAR(180) NOT NULL,
    responsible VARCHAR(160) NOT NULL,
    due_on DATE NOT NULL,
    notes TEXT,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_sales_activity_company_due
    ON sales_activities(company_id, completed_at, due_on, id);

CREATE INDEX idx_sales_activity_opportunity
    ON sales_activities(company_id, opportunity_id, id);

CREATE INDEX idx_sales_activity_partner
    ON sales_activities(company_id, business_partner_id, id);
