package com.finemp.finance.entry;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EntrySeriesCommandReceiptTest {
    @Test void receiptMustKeepTheOriginalOrderedImmutableBoundedSet() {
        var company=UUID.randomUUID();var command=UUID.randomUUID();var group=UUID.randomUUID();var id=UUID.randomUUID();
        var source=new ArrayList<>(List.of(new EntrySeriesCommandReceipt.Item(1,id,0),new EntrySeriesCommandReceipt.Item(2,UUID.randomUUID(),0)));
        var receipt=new EntrySeriesCommandReceipt(company,command,group,source);source.clear();assertThat(receipt.items()).hasSize(2);
        assertThatThrownBy(()->receipt.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->new EntrySeriesCommandReceipt(company,command,group,List.of(new EntrySeriesCommandReceipt.Item(2,id,0),new EntrySeriesCommandReceipt.Item(1,UUID.randomUUID(),0)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EntrySeriesCommandReceipt(company,command,group,List.of(new EntrySeriesCommandReceipt.Item(1,id,0),new EntrySeriesCommandReceipt.Item(2,id,0)))).isInstanceOf(IllegalArgumentException.class);
    }
}
