CREATE TABLE company_client_assignment_events (
    id UUID NOT NULL,
    company_id UUID NOT NULL,
    client_id UUID NOT NULL,
    assigned_by_user_account_id UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_company_client_assignment_events PRIMARY KEY (id),

    CONSTRAINT fk_company_client_assignment_events_company
        FOREIGN KEY (company_id)
        REFERENCES companies (id),

    CONSTRAINT fk_company_client_assignment_events_client
        FOREIGN KEY (client_id)
        REFERENCES clients (id),

    CONSTRAINT fk_company_client_assignment_events_user
        FOREIGN KEY (assigned_by_user_account_id)
        REFERENCES user_accounts (id)
);

CREATE INDEX ix_company_client_assignment_events_company_time
    ON company_client_assignment_events (company_id, assigned_at DESC);
