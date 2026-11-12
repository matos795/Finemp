CREATE TABLE planning_meetings (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    title VARCHAR(160) NOT NULL,
    stage VARCHAR(80) NOT NULL,
    scheduled_on DATE NOT NULL,
    notes TEXT,
    diagnostic_text TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_planning_meetings_company_date
    ON planning_meetings(company_id, scheduled_on, id);

CREATE TABLE planning_goals (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    name VARCHAR(180) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    baseline_value NUMERIC(19,4) NOT NULL,
    target_value NUMERIC(19,4) NOT NULL,
    due_on DATE NOT NULL,
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_planning_goal_direction
        CHECK (direction IN ('UP', 'DOWN')),
    CONSTRAINT chk_planning_goal_target_differs_from_baseline
        CHECK (target_value <> baseline_value)
);

CREATE INDEX idx_planning_goals_company_due
    ON planning_goals(company_id, active, due_on, id);

CREATE TABLE goal_measurements (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    goal_id UUID NOT NULL REFERENCES planning_goals(id) ON DELETE CASCADE,
    measured_on DATE NOT NULL,
    measured_value NUMERIC(19,4) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_goal_measurements_goal_date
    ON goal_measurements(goal_id, measured_on DESC, created_at DESC, id);

CREATE INDEX idx_goal_measurements_company
    ON goal_measurements(company_id, goal_id);

CREATE TABLE action_plan_items (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    goal_id UUID REFERENCES planning_goals(id) ON DELETE SET NULL,
    what_text VARCHAR(240) NOT NULL,
    why_text TEXT,
    responsible VARCHAR(160) NOT NULL,
    due_on DATE,
    where_text VARCHAR(200),
    how_text TEXT,
    cost NUMERIC(19,2),
    workflow_status VARCHAR(40) NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_action_plan_cost_non_negative
        CHECK (cost IS NULL OR cost >= 0)
);

CREATE INDEX idx_action_plan_company_status_due
    ON action_plan_items(company_id, workflow_status, due_on, id);
