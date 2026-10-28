ALTER TABLE financial_entry_command_receipts
    DROP CONSTRAINT financial_entry_command_receipts_action_check,
    ADD CONSTRAINT financial_entry_command_receipts_action_check
        CHECK(action IN ('CREATED','UPDATED','SETTLED','SETTLEMENT_REVERSED','DELETED'));

ALTER TABLE financial_entry_events
    ALTER COLUMN after_version DROP NOT NULL,
    ALTER COLUMN after_state DROP NOT NULL,
    DROP CONSTRAINT financial_entry_events_action_check,
    ADD CONSTRAINT financial_entry_events_action_check
        CHECK(action IN ('CREATED','UPDATED','SETTLED','SETTLEMENT_REVERSED','DELETED')),
    DROP CONSTRAINT ck_financial_entry_event_transition,
    ADD CONSTRAINT ck_financial_entry_event_transition CHECK(
        (action='CREATED' AND before_version IS NULL AND before_state IS NULL
            AND after_version IS NOT NULL AND after_version=0 AND after_state IS NOT NULL)
        OR (action IN ('UPDATED','SETTLED','SETTLEMENT_REVERSED')
            AND before_version IS NOT NULL AND before_state IS NOT NULL
            AND after_version IS NOT NULL AND after_version>=before_version AND after_state IS NOT NULL)
        OR (action='DELETED' AND before_version IS NOT NULL AND before_state IS NOT NULL
            AND after_version IS NULL AND after_state IS NULL));
-- Existing hashes and audit states are preserved. Deletion has no posterior resource version/state.
