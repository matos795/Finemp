CREATE TABLE sales_stages (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    name VARCHAR(100) NOT NULL,
    position INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_sales_stage_position_non_negative
        CHECK (position >= 0),
    CONSTRAINT uk_sales_stage_company_name
        UNIQUE (company_id, name),
    CONSTRAINT uk_sales_stage_company_position
        UNIQUE (company_id, position)
);

CREATE INDEX idx_sales_stages_company_active_position
    ON sales_stages(company_id, active, position, id);

CREATE TABLE sales_opportunities (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    business_partner_id UUID REFERENCES business_partners(id),
    stage_id UUID NOT NULL REFERENCES sales_stages(id),
    title VARCHAR(180) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    monthly_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    probability INTEGER NOT NULL,
    responsible VARCHAR(160) NOT NULL,
    expected_close_on DATE NOT NULL,
    outcome VARCHAR(10) NOT NULL,
    opened_on DATE NOT NULL,
    closed_on DATE,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_sales_opportunity_amount_non_negative
        CHECK (amount >= 0),
    CONSTRAINT chk_sales_opportunity_probability
        CHECK (probability BETWEEN 0 AND 100),
    CONSTRAINT chk_sales_opportunity_outcome
        CHECK (outcome IN ('OPEN', 'WON', 'LOST')),
    CONSTRAINT chk_sales_opportunity_closed_state
        CHECK (
            (outcome = 'OPEN' AND closed_on IS NULL)
            OR
            (outcome IN ('WON', 'LOST') AND closed_on IS NOT NULL)
        )
);

CREATE INDEX idx_sales_opportunities_company_stage
    ON sales_opportunities(company_id, outcome, stage_id, expected_close_on, id);

CREATE INDEX idx_sales_opportunities_company_partner
    ON sales_opportunities(company_id, business_partner_id, id);

CREATE TABLE sales_opportunity_stage_history (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    opportunity_id UUID NOT NULL REFERENCES sales_opportunities(id) ON DELETE CASCADE,
    stage_id UUID NOT NULL REFERENCES sales_stages(id),
    stage_name VARCHAR(100) NOT NULL,
    stage_position INTEGER NOT NULL,
    actor_user_account_id UUID NOT NULL REFERENCES user_accounts(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_sales_stage_history_position_non_negative
        CHECK (stage_position >= 0)
);

CREATE INDEX idx_sales_stage_history_opportunity
    ON sales_opportunity_stage_history(
        company_id,
        opportunity_id,
        occurred_at,
        id
    );

CREATE TABLE sales_activities (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    opportunity_id UUID REFERENCES sales_opportunities(id) ON DELETE SET NULL,
    business_partner_id UUID REFERENCES business_partners(id),
    title VARCHAR(180) NOT NULL,
    notes TEXT,
    responsible VARCHAR(160) NOT NULL,
    due_on DATE NOT NULL,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_sales_activity_terminal_state
        CHECK (completed_at IS NULL OR cancelled_at IS NULL)
);

CREATE INDEX idx_sales_activities_company_due
    ON sales_activities(company_id, due_on, completed_at, cancelled_at, id);

CREATE INDEX idx_sales_activities_opportunity
    ON sales_activities(company_id, opportunity_id, id);

CREATE INDEX idx_sales_activities_partner
    ON sales_activities(company_id, business_partner_id, id);
