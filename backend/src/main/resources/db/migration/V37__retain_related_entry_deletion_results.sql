ALTER TABLE financial_entry_command_receipts
    ADD COLUMN deleted_entries TEXT,
    ADD CONSTRAINT ck_entry_receipt_deleted_entries CHECK(deleted_entries IS NULL OR (
        action='DELETED' AND jsonb_typeof(deleted_entries::jsonb)='array'
        AND jsonb_array_length(deleted_entries::jsonb) BETWEEN 1 AND 120));
-- NULL preserves legacy receipt representation; its deletion affected only the root.
