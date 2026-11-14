CREATE TABLE assets (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    asset_tag VARCHAR(30),
    description VARCHAR(150) NOT NULL,
    category VARCHAR(30) NOT NULL,
    brand_model VARCHAR(80),
    serial_number VARCHAR(60),
    acquisition_date DATE NOT NULL,
    acquisition_value NUMERIC(19,2) NOT NULL,
    quantity INTEGER NOT NULL,
    business_partner_id UUID REFERENCES business_partners(id),
    document_reference VARCHAR(60),
    result_center_id UUID REFERENCES operational_result_centers(id),
    location VARCHAR(80),
    responsible VARCHAR(120),
    useful_life_months INTEGER NOT NULL,
    residual_percent NUMERIC(5,2) NOT NULL,
    depreciable BOOLEAN NOT NULL,
    depreciation_start_date DATE,
    status VARCHAR(20) NOT NULL,
    warranty_until DATE,
    notes VARCHAR(800),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_assets_description CHECK (char_length(trim(description)) >= 2),
    CONSTRAINT ck_assets_category CHECK (
        category IN ('IT','VEHICLE','MACHINERY','FURNITURE','INSTALLATION','BUILDING','LAND','INTANGIBLE','OTHER')
    ),
    CONSTRAINT ck_assets_value CHECK (acquisition_value > 0),
    CONSTRAINT ck_assets_quantity CHECK (quantity >= 1),
    CONSTRAINT ck_assets_useful_life CHECK (
        useful_life_months >= 0
        AND (NOT depreciable OR useful_life_months >= 1)
    ),
    CONSTRAINT ck_assets_residual CHECK (residual_percent BETWEEN 0 AND 90),
    CONSTRAINT ck_assets_status CHECK (status IN ('IN_USE','MAINTENANCE','IDLE','DISPOSED')),
    CONSTRAINT ck_assets_depreciation_start CHECK (
        depreciation_start_date IS NULL OR depreciation_start_date >= acquisition_date
    )
);

CREATE UNIQUE INDEX uq_assets_company_tag
    ON assets(company_id, lower(trim(asset_tag)))
    WHERE asset_tag IS NOT NULL AND trim(asset_tag) <> '';

CREATE INDEX idx_assets_company_status
    ON assets(company_id, status);

CREATE INDEX idx_assets_company_category
    ON assets(company_id, category);

CREATE INDEX idx_assets_company_partner
    ON assets(company_id, business_partner_id);

CREATE INDEX idx_assets_company_result_center
    ON assets(company_id, result_center_id);
