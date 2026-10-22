CREATE TABLE user_accounts (
    id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_user_accounts PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_user_accounts_email_lower
    ON user_accounts (LOWER(email));

CREATE TABLE company_access_grants (
    id UUID NOT NULL,
    user_account_id UUID NOT NULL,
    company_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_company_access_grants PRIMARY KEY (id),

    CONSTRAINT fk_company_access_grants_user_account
        FOREIGN KEY (user_account_id)
        REFERENCES user_accounts(id),

    CONSTRAINT fk_company_access_grants_company
        FOREIGN KEY (company_id)
        REFERENCES companies(id),

    CONSTRAINT uk_company_access_grants_user_company
        UNIQUE (user_account_id, company_id)
);