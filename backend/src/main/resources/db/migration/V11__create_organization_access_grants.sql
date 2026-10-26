CREATE TABLE user_account_platform_permissions (
    user_account_id UUID NOT NULL,
    permission VARCHAR(80) NOT NULL,

    CONSTRAINT pk_user_account_platform_permissions
        PRIMARY KEY (user_account_id, permission),

    CONSTRAINT fk_user_account_platform_permissions_user
        FOREIGN KEY (user_account_id)
        REFERENCES user_accounts (id)
        ON DELETE CASCADE
);

CREATE TABLE accounting_firm_access_grants (
    id UUID NOT NULL,
    user_account_id UUID NOT NULL,
    accounting_firm_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_accounting_firm_access_grants
        PRIMARY KEY (id),

    CONSTRAINT fk_accounting_firm_access_grants_user
        FOREIGN KEY (user_account_id)
        REFERENCES user_accounts (id),

    CONSTRAINT fk_accounting_firm_access_grants_firm
        FOREIGN KEY (accounting_firm_id)
        REFERENCES accounting_firms (id),

    CONSTRAINT uk_accounting_firm_access_grants_user_firm
        UNIQUE (user_account_id, accounting_firm_id)
);

CREATE INDEX ix_accounting_firm_access_grants_firm_user
    ON accounting_firm_access_grants (accounting_firm_id, user_account_id);

CREATE TABLE accounting_firm_access_grant_permissions (
    accounting_firm_access_grant_id UUID NOT NULL,
    permission VARCHAR(80) NOT NULL,

    CONSTRAINT pk_accounting_firm_access_grant_permissions
        PRIMARY KEY (accounting_firm_access_grant_id, permission),

    CONSTRAINT fk_accounting_firm_access_grant_permissions_grant
        FOREIGN KEY (accounting_firm_access_grant_id)
        REFERENCES accounting_firm_access_grants (id)
        ON DELETE CASCADE
);

