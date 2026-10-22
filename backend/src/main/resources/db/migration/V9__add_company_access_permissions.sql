ALTER TABLE company_access_grants
    ADD COLUMN access_role VARCHAR(30) NOT NULL DEFAULT 'CUSTOM',
    ADD COLUMN cost_center_scope VARCHAR(20) NOT NULL DEFAULT 'ALL';

CREATE TABLE company_access_grant_permissions (
    company_access_grant_id UUID NOT NULL,
    permission VARCHAR(80) NOT NULL,

    CONSTRAINT pk_company_access_grant_permissions
        PRIMARY KEY (company_access_grant_id, permission),

    CONSTRAINT fk_company_access_grant_permissions_grant
        FOREIGN KEY (company_access_grant_id)
        REFERENCES company_access_grants(id)
        ON DELETE CASCADE
);