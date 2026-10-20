CREATE TABLE dre_classifications (
    id UUID NOT NULL,
    dre_classification VARCHAR(255) NOT NULL,
    dre_open_classification VARCHAR(255) NOT NULL,
    ebitda_classification VARCHAR(255) NOT NULL,

    CONSTRAINT pk_dre_classifications
        PRIMARY KEY (id),

    CONSTRAINT uk_dre_classifications_levels
        UNIQUE (
            dre_classification,
            dre_open_classification,
            ebitda_classification
        )
);