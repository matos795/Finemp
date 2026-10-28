CREATE INDEX idx_financial_entry_reference_date
    ON financial_entries(company_id,(COALESCE(payment_date,due_date)),id);
