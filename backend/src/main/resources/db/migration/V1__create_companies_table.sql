CREATE TABLE companies (
    id UUID NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    cnpj VARCHAR(14) NOT NULL,

    CONSTRAINT pk_companies PRIMARY KEY (id)
);