ALTER TABLE financial_entry_command_receipts
    DROP CONSTRAINT financial_entry_command_receipts_action_check,
    ADD CONSTRAINT financial_entry_command_receipts_action_check
        CHECK(action IN ('CREATED','UPDATED','SETTLED','SETTLEMENT_REVERSED'));

ALTER TABLE financial_entry_events
    DROP CONSTRAINT financial_entry_events_action_check,
    ADD CONSTRAINT financial_entry_events_action_check
        CHECK(action IN ('CREATED','UPDATED','SETTLED','SETTLEMENT_REVERSED')),
    DROP CONSTRAINT ck_financial_entry_event_transition,
    ADD CONSTRAINT ck_financial_entry_event_transition CHECK(
        (action='CREATED' AND before_version IS NULL AND before_state IS NULL AND after_version=0)
        OR (action IN ('UPDATED','SETTLED','SETTLEMENT_REVERSED')
            AND before_version IS NOT NULL AND before_state IS NOT NULL AND after_version>=before_version));
-- Existing entries, events, receipts and CREATE/UPDATE fingerprints are preserved.
