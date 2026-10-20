ALTER TABLE accounting_account_classifications
    ADD COLUMN dre_classification_id UUID;


INSERT INTO dre_classifications (
    id,
    dre_classification,
    dre_open_classification,
    ebitda_classification
)
SELECT DISTINCT ON (
    classification.dre_classification,
    classification.dre_open_classification,
    classification.ebitda_classification
)
    classification.id,
    classification.dre_classification,
    classification.dre_open_classification,
    classification.ebitda_classification
FROM accounting_account_classifications classification
WHERE classification.dre_classification IS NOT NULL
  AND classification.dre_open_classification IS NOT NULL
  AND classification.ebitda_classification IS NOT NULL
ORDER BY
    classification.dre_classification,
    classification.dre_open_classification,
    classification.ebitda_classification,
    classification.id;


UPDATE accounting_account_classifications account_classification
SET dre_classification_id = dre_classification.id
FROM dre_classifications dre_classification
WHERE account_classification.dre_classification IS NOT NULL
  AND account_classification.dre_open_classification IS NOT NULL
  AND account_classification.ebitda_classification IS NOT NULL
  AND dre_classification.dre_classification =
      account_classification.dre_classification
  AND dre_classification.dre_open_classification =
      account_classification.dre_open_classification
  AND dre_classification.ebitda_classification =
      account_classification.ebitda_classification;


ALTER TABLE accounting_account_classifications
    ADD CONSTRAINT fk_accounting_account_classifications_dre
        FOREIGN KEY (dre_classification_id)
        REFERENCES dre_classifications (id);


CREATE INDEX idx_accounting_account_classifications_dre_classification
    ON accounting_account_classifications (dre_classification_id);