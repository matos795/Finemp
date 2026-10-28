package com.finemp.finance.entry;

import com.finemp.finance.category.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EntrySettlementTest {
    final UUID originalBank=UUID.randomUUID(),paidBank=UUID.randomUUID(),category=UUID.randomUUID(),partner=UUID.randomUUID(),center=UUID.randomUUID();
    final LocalDate paidDate=LocalDate.of(2026,7,10);
    @Test void settlementMustChangePaymentAndBankWithoutChangingNominalOrOtherFinancialFields() {
        var before=unpaid("100.00");var after=EntrySettlement.settle(before,new EntrySettlementData(paidDate,new BigDecimal("110.50"),paidBank));
        assertThat(after.nominalAmount()).isEqualByComparingTo("100.00");assertThat(after.actualPaidAmount()).isEqualByComparingTo("110.50");
        assertThat(after.effectiveAmount()).isEqualByComparingTo("110.50");assertThat(after.paymentDate()).isEqualTo(paidDate);assertThat(after.bankAccountId()).isEqualTo(paidBank);
        assertOtherFields(before,after);
    }
    @Test void equalCanonicalPaidAmountMustRemoveTheOverrideWhileOneCentDifferenceMustKeepIt() {
        for(var amount:new String[]{"99.99","100.01"}) {
            var result=EntrySettlement.settle(unpaid("100"),new EntrySettlementData(paidDate,new BigDecimal(amount),paidBank));
            assertThat(result.actualPaidAmount()).isEqualByComparingTo(amount);
        }
        var previous=EntrySettlement.settle(unpaid("100"),new EntrySettlementData(paidDate,new BigDecimal("110"),paidBank));
        var equal=EntrySettlement.settle(previous,new EntrySettlementData(paidDate,new BigDecimal("100.000"),paidBank));
        assertThat(equal.actualPaidAmount()).isNull();assertThat(equal.effectiveAmount()).isEqualByComparingTo("100.00");
    }
    @Test void reversalMustClearPaymentAndActualAmountButRetainTheMostRecentlySelectedBank() {
        var paid=EntrySettlement.settle(unpaid("100"),new EntrySettlementData(paidDate,new BigDecimal("90"),paidBank));
        var reversed=EntrySettlement.reverse(paid);
        assertThat(reversed.paymentDate()).isNull();assertThat(reversed.actualPaidAmount()).isNull();assertThat(reversed.bankAccountId()).isEqualTo(paidBank);
        assertThat(reversed.effectiveAmount()).isEqualByComparingTo("100.00");assertOtherFields(paid,reversed);
        assertThat(EntrySettlement.reverse(reversed)).isEqualTo(reversed);
    }
    @Test void applyingSettlementAgainMustReplaceItsPaymentDataAsTheHomologatedHandlerDoes() {
        var first=EntrySettlement.settle(unpaid("100"),new EntrySettlementData(paidDate,new BigDecimal("90"),paidBank));
        var next=EntrySettlement.settle(first,new EntrySettlementData(paidDate.plusDays(2),new BigDecimal("101"),originalBank));
        assertThat(next.paymentDate()).isEqualTo(paidDate.plusDays(2));assertThat(next.actualPaidAmount()).isEqualByComparingTo("101.00");
        assertThat(next.bankAccountId()).isEqualTo(originalBank);assertOtherFields(first,next);
    }
    @Test void generatedZeroNominalMustRemainZeroWhenSettledForAPositiveCanonicalAmount() {
        var paid=EntrySettlement.settle(unpaid("0"),new EntrySettlementData(paidDate,new BigDecimal("1.00"),paidBank));
        assertThat(paid.nominalAmount()).isEqualByComparingTo("0.00");assertThat(paid.actualPaidAmount()).isEqualByComparingTo("1.00");
    }
    @Test void canonicalInputMustNotSilentlyRoundOrAcceptInvalidAmountDateOrBank() {
        for(var value:new String[]{"0","-1","1.001","1E+1000000","1E-1000000"})
            assertThatThrownBy(()->new EntrySettlementData(paidDate,new BigDecimal(value),paidBank)).isInstanceOf(IllegalArgumentException.class);
        for(var date:new LocalDate[]{null,LocalDate.of(0,1,1),LocalDate.of(10000,1,1)})
            assertThatThrownBy(()->new EntrySettlementData(date,BigDecimal.ONE,paidBank)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EntrySettlementData(paidDate,BigDecimal.ONE,null)).isInstanceOf(IllegalArgumentException.class);
        var canonical=new EntrySettlementData(paidDate,new BigDecimal("1.2300"),paidBank);
        assertThat(canonical.paidAmount()).isEqualTo(new BigDecimal("1.23"));assertThat(canonical.toString()).doesNotContain("1.23",paidBank.toString());
    }
    FinancialEntryValues unpaid(String nominal) {
        var date=LocalDate.of(2026,7,1);
        return new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic private entry",new BigDecimal(nominal),date,date.plusDays(1),YearMonth.of(2026,6),date.plusDays(3),
                null,null,originalBank,category,partner,center,CostBehavior.VARIABLE,"Synthetic method","Synthetic document","Synthetic note");
    }
    void assertOtherFields(FinancialEntryValues first,FinancialEntryValues second) {
        assertThat(second).usingRecursiveComparison().ignoringFields("paymentDate","actualPaidAmount","bankAccountId").isEqualTo(first);
    }
}
