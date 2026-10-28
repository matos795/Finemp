package com.finemp.finance.entry;

import com.finemp.finance.category.FinancialDirection;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FinancialEntryCommandDataTest {
    private final UUID bank=UUID.randomUUID(),category=UUID.randomUUID();
    @Test void canonicalInputMustNotRoundRawAmountsOrAcceptNonpositiveManualAmounts() {
        for(var amount:new String[]{"0","-1","1.001","1E+1000000","1E-1000000"})
            assertThatThrownBy(()->data(amount,"Descrição","","")).isInstanceOf(IllegalArgumentException.class);
        assertThat(data("1.2300"," Descrição ",null,null).nominalAmount()).isEqualTo(new BigDecimal("1.23"));
    }
    @Test void updateMustPreserveAnExistingSettlementOnlyWhileStillPaid() {
        var paid=data("100","Descrição","","").values(new BigDecimal("110.00"));
        assertThat(paid.actualPaidAmount()).isEqualByComparingTo("110.00");
        var unpaid=new FinancialEntryCommandData(paid.direction(),paid.description(),paid.nominalAmount(),paid.documentDate(),paid.entryDate(),paid.competence(),
                paid.dueDate(),null,bank,category,null,null,null,"","","");
        assertThat(unpaid.values(new BigDecimal("110.00")).actualPaidAmount()).isNull();
    }
    @Test void fingerprintMustBeCanonicalVersionedAndUnambiguousAcrossFieldsAndOperations() {
        var actor=UUID.randomUUID();var company=UUID.randomUUID();var target=UUID.randomUUID();
        var first=data("1.00","Descrição","a|b","c");var second=data("1","Descrição","a","b|c");
        var hash=EntryCommandFingerprint.of(company,actor,EntryCommandAction.CREATED,null,null,first);
        assertThat(hash).matches("[a-f0-9]{64}").isEqualTo(EntryCommandFingerprint.of(company,actor,EntryCommandAction.CREATED,null,null,data("1.000"," Descrição ","a|b","c")));
        assertThat(hash).isNotEqualTo(EntryCommandFingerprint.of(company,actor,EntryCommandAction.CREATED,null,null,second));
        assertThat(hash).isNotEqualTo(EntryCommandFingerprint.of(company,actor,EntryCommandAction.UPDATED,target,0L,first));
        assertThat(hash).isNotEqualTo(EntryCommandFingerprint.of(company,UUID.randomUUID(),EntryCommandAction.CREATED,null,null,first));
        assertThat(first.toString()).doesNotContain("Descrição","a|b");
    }
    private FinancialEntryCommandData data(String amount,String description,String doc,String notes) {
        var date=LocalDate.of(2026,7,1);
        return new FinancialEntryCommandData(FinancialDirection.EXPENSE,description,new BigDecimal(amount),date,date,YearMonth.of(2026,7),date,date,
                bank,category,null,null,null,"",doc,notes);
    }
}
