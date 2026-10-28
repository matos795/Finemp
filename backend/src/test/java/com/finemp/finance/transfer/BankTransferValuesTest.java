package com.finemp.finance.transfer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BankTransferValuesTest {
    final UUID origin=UUID.randomUUID(),destination=UUID.randomUUID();
    final LocalDate date=LocalDate.of(2026,7,1);
    @Test void canonicalStateMustPreserveExactCentsAndNotChooseARawCapturePolicy() {
        assertThat(values("1.2300"," Transfer ").amount()).isEqualTo(new BigDecimal("1.23"));
        assertThat(values("99999999999999999.99","").amount()).isEqualTo(new BigDecimal("99999999999999999.99"));
        assertThat(values("0E-1000000","").amount()).isEqualTo(new BigDecimal("0.00"));
    }
    @Test void negativeNonCentAndUnboundedAmountsMustBeRejectedBeforePersistence() {
        for(var amount:new String[]{"-0.01","1.001","1E+1000000","1E-1000000","100000000000000000.00"})
            assertThatThrownBy(()->values(amount,"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BankTransferValues(origin,destination,date,null,"")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void bothAccountsMustBePresentAndDifferent() {
        assertThatThrownBy(()->new BankTransferValues(origin,origin,date,BigDecimal.ONE,"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BankTransferValues(null,destination,date,BigDecimal.ONE,"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BankTransferValues(origin,null,date,BigDecimal.ONE,"")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void transferDateMustBeAnExplicitSupportedIsoYearWithoutInventedBankOpeningChronology() {
        for(var invalid:new LocalDate[]{null,LocalDate.of(0,1,1),LocalDate.of(10000,1,1)})
            assertThatThrownBy(()->new BankTransferValues(origin,destination,invalid,BigDecimal.ONE,"")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new BankTransferValues(origin,destination,LocalDate.of(1,1,1),BigDecimal.ONE,"").transferDate()).isEqualTo(LocalDate.of(1,1,1));
    }
    @Test void descriptionIsOptionalTrimmedAndBoundedRatherThanRequiredLikeAnEntryDescription() {
        assertThat(values("1",null).description()).isEmpty();assertThat(values("1"," Description ").description()).isEqualTo("Description");
        assertThatThrownBy(()->values("1","x".repeat(256))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unsupportedDatabaseTextMustNotBeSilentlyReplacedDuringEncoding() {
        for(var description:new String[]{"Bad "+(char)0,"Bad "+(char)0xD800,"Bad "+(char)0xDC00})
            assertThatThrownBy(()->values("1",description)).isInstanceOf(IllegalArgumentException.class);
        var emoji=new String(Character.toChars(0x1F600));assertThat(values("1","Valid "+emoji).description()).contains(emoji);
    }
    @Test void diagnosticRepresentationMustNotDiscloseFinancialTextOrAccountIdentities() {
        assertThat(values("123.45","Private description").toString()).doesNotContain("123.45","Private description",origin.toString(),destination.toString());
    }
    BankTransferValues values(String amount,String description) {return new BankTransferValues(origin,destination,date,new BigDecimal(amount),description);}
}
