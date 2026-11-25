-- Gestão de pessoas, primeira fatia operacional (#462).
-- Minimização de dados: sem documento pessoal, data de nascimento, salário,
-- endereço ou contato. Remuneração e regras trabalhistas ficam fora (#45).

CREATE TABLE job_positions (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    title VARCHAR(120) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_job_position_title_not_blank CHECK (length(trim(title)) > 0)
);

CREATE UNIQUE INDEX uk_job_positions_company_title
    ON job_positions(company_id, lower(trim(title)));

CREATE INDEX idx_job_positions_company_active
    ON job_positions(company_id, active, title, id);

CREATE TABLE employees (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    full_name VARCHAR(160) NOT NULL,
    employment_type VARCHAR(3) NOT NULL,
    job_position_id UUID REFERENCES job_positions(id),
    result_center_id UUID REFERENCES operational_result_centers(id),
    hired_on DATE NOT NULL,
    terminated_on DATE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_employee_name_not_blank CHECK (length(trim(full_name)) > 0),
    CONSTRAINT chk_employee_type CHECK (employment_type IN ('CLT', 'APR', 'PRO', 'EST', 'PJ')),
    CONSTRAINT chk_employee_termination_after_hire
        CHECK (terminated_on IS NULL OR terminated_on >= hired_on)
);

CREATE INDEX idx_employees_company_name
    ON employees(company_id, full_name, id);

CREATE INDEX idx_employees_company_position
    ON employees(company_id, job_position_id);

CREATE INDEX idx_employees_company_center
    ON employees(company_id, result_center_id);
