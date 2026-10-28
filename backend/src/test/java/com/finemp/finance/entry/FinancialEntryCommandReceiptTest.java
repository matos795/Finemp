package com.finemp.finance.entry;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class FinancialEntryCommandReceiptTest {
    @Test void additiveDeletionSetMustNotChangeOtherActionPayloadsAndLegacyConstructorRetainsTheRoot() {
        var json=JsonMapper.builder().build();var company=UUID.randomUUID();var command=UUID.randomUUID();var entry=UUID.randomUUID();
        var created=new FinancialEntryCommandReceipt(company,command,entry,EntryCommandAction.CREATED,0);
        assertThat(json.readTree(json.writeValueAsString(created)).get("deletedEntries")).isNull();
        var deleted=new FinancialEntryCommandReceipt(company,command,entry,EntryCommandAction.DELETED,1);
        var set=json.readTree(json.writeValueAsString(deleted)).get("deletedEntries");assertThat(set.size()).isEqualTo(1);assertThat(set.get(0).get("entryId").asString()).isEqualTo(entry.toString());
        assertThatThrownBy(()->deleted.deletedEntries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
