CREATE TABLE operational_result_centers (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    parent_id UUID,
    code VARCHAR(20) NOT NULL CHECK(code ~ '^[0-9]+(\.[0-9]+)*$'),
    name VARCHAR(80) NOT NULL CHECK(length(trim(name)) >= 2),
    search_name VARCHAR(160) NOT NULL,
    sort_key VARCHAR(300) COLLATE "C" NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version >= 0),
    CONSTRAINT uq_result_center_company_id UNIQUE(company_id,id),
    CONSTRAINT uq_result_center_company_code UNIQUE(company_id,code),
    CONSTRAINT fk_result_center_parent FOREIGN KEY(company_id,parent_id) REFERENCES operational_result_centers(company_id,id),
    CONSTRAINT ck_result_center_not_self CHECK(parent_id IS NULL OR parent_id<>id)
);
CREATE INDEX idx_result_center_company_parent ON operational_result_centers(company_id,parent_id);
CREATE INDEX idx_result_center_company_order ON operational_result_centers(company_id,sort_key,id);

CREATE TABLE financial_company_settings (
    company_id UUID PRIMARY KEY REFERENCES companies(id),
    require_result_center BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version >= 0)
);
