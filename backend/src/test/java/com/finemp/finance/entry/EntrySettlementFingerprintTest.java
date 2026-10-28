package com.finemp.finance.entry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EntrySettlementFingerprintTest {
    final UUID company=UUID.randomUUID(),actor=UUID.randomUUID(),entry=UUID.randomUUID(),bank=UUID.randomUUID();
    final LocalDate date=LocalDate.of(2026,7,10);
    @Test void equivalentCanonicalAmountsMustProduceTheSameStableFingerprint() {
        var first=hash(company,actor,entry,0,date,"1.2300",bank);
        assertThat(first).matches("[a-f0-9]{64}").isEqualTo(hash(company,actor,entry,0,date,"1.23",bank));
    }
    @Test void everyIntentAndSecurityContextFieldMustParticipateInTheFingerprint() {
        var baseline=hash(company,actor,entry,0,date,"1.23",bank);
        assertThat(List.of(hash(UUID.randomUUID(),actor,entry,0,date,"1.23",bank),hash(company,UUID.randomUUID(),entry,0,date,"1.23",bank),
                hash(company,actor,UUID.randomUUID(),0,date,"1.23",bank),hash(company,actor,entry,1,date,"1.23",bank),
                hash(company,actor,entry,0,date.plusDays(1),"1.23",bank),hash(company,actor,entry,0,date,"1.24",bank),
                hash(company,actor,entry,0,date,"1.23",UUID.randomUUID()))).doesNotContain(baseline).doesNotHaveDuplicates();
    }
    @Test void reversalMustHaveItsOwnActionIdentityAndExpectedVersion() {
        var reversal=EntrySettlementFingerprint.reversal(company,actor,entry,0);
        assertThat(reversal).matches("[a-f0-9]{64}").isEqualTo(EntrySettlementFingerprint.reversal(company,actor,entry,0));
        assertThat(reversal).isNotEqualTo(EntrySettlementFingerprint.reversal(company,actor,entry,1));
        assertThat(reversal).isNotEqualTo(hash(company,actor,entry,0,date,"1.23",bank));
    }
    String hash(UUID company,UUID actor,UUID entry,long version,LocalDate date,String amount,UUID bank) {
        return EntrySettlementFingerprint.settlement(company,actor,entry,version,new EntrySettlementData(date,new BigDecimal(amount),bank));
    }
}
