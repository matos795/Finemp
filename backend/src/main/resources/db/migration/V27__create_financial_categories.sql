CREATE TABLE financial_categories (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    parent_id UUID,
    kind VARCHAR(20) NOT NULL CHECK(kind IN ('GROUP','SUBCATEGORY')),
    direction VARCHAR(20) NOT NULL CHECK(direction IN ('INCOME','EXPENSE')),
    code VARCHAR(64) NOT NULL,
    sequence_number BIGINT NOT NULL CHECK(sequence_number >= 1),
    sort_key VARCHAR(64) COLLATE "C" NOT NULL,
    name VARCHAR(255) NOT NULL CHECK(length(trim(name)) > 0),
    search_name VARCHAR(510) NOT NULL,
    cash_activity VARCHAR(20) CHECK(cash_activity IN ('OPERATING','INVESTING','FINANCING')),
    dre_line VARCHAR(10) CHECK(dre_line IN ('REC','DED','CUS','DCOM','DADM','DPES','DTRI','OUT','DEP','RFIN','DFIN','IR','NAO')),
    cost_behavior VARCHAR(20) CHECK(cost_behavior IN ('FIXED','VARIABLE')),
    version BIGINT NOT NULL DEFAULT 0 CHECK(version >= 0),
    CONSTRAINT uq_financial_category_company_id UNIQUE(company_id,id),
    CONSTRAINT uq_financial_category_company_code UNIQUE(company_id,code),
    CONSTRAINT fk_financial_category_parent FOREIGN KEY(company_id,parent_id) REFERENCES financial_categories(company_id,id),
    CONSTRAINT ck_financial_category_kind CHECK(
        (kind='GROUP' AND parent_id IS NULL AND cash_activity IS NOT NULL AND dre_line IS NOT NULL)
        OR (kind='SUBCATEGORY' AND parent_id IS NOT NULL AND parent_id<>id))
);
CREATE INDEX idx_financial_category_parent ON financial_categories(company_id,parent_id);
CREATE INDEX idx_financial_category_order ON financial_categories(company_id,sort_key,id);
