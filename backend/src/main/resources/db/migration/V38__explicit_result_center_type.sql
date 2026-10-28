ALTER TABLE operational_result_centers ADD COLUMN type VARCHAR(20);

-- Preserve every historical reference. Only unused parents migrate to GROUP.
UPDATE operational_result_centers center SET type = CASE
    WHEN EXISTS(SELECT 1 FROM operational_result_centers child
                WHERE child.company_id=center.company_id AND child.parent_id=center.id)
     AND NOT EXISTS(SELECT 1 FROM financial_entries entry
                    WHERE entry.company_id=center.company_id AND entry.result_center_id=center.id)
    THEN 'GROUP' ELSE 'ANALYTICAL' END;

ALTER TABLE operational_result_centers ALTER COLUMN type SET NOT NULL;
ALTER TABLE operational_result_centers ADD CONSTRAINT ck_result_center_type
    CHECK(type IN ('GROUP','ANALYTICAL'));
