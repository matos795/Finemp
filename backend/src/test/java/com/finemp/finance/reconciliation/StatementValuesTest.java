package com.finemp.finance.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StatementValuesTest {
    final LocalDate date=LocalDate.of(2026,7,1);
    @Test void fitIdTakesPrecedenceAndRemainsCaseSensitive() {
        assertThat(value("1.00","Nome", " ID-1 ").identityKey()).isEqualTo(value("9.99","Outro","ID-1").identityKey());
        assertThat(value("1","Nome","ID-1").identityKey()).isNotEqualTo(value("1","Nome","id-1").identityKey());
        assertThat(value("1","Nome","ID-1").identityKey()).isNotEqualTo(value("1","Nome","").identityKey());
    }
    @Test void fallbackUsesDateSignedExactValueAndNormalizedMemo() {
        var first=value("-1.20","  TÁXÍ\u00a0","");var same=value("-1.2","taxi",null);
        assertThat(first.identityKey()).isEqualTo(same.identityKey());assertThat(first.identityKey()).hasSize(64);
        assertThat(first.identityKey()).isNotEqualTo(value("1.20","taxi","").identityKey());assertThat(first.identityKey()).isNotEqualTo(new StatementValues(date.plusDays(1),new BigDecimal("-1.20"),"taxi","","").identityKey());
    }
    @Test void inputMustRejectSubcentZeroOversizedAndInvalidTextRatherThanRound() {
        for(var amount:java.util.List.of("0","0.001","1.005","100000000000000000.00"))assertThatThrownBy(()->value(amount,"Memo","")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->value("1","bad\u0000memo","")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->value("1","x".repeat(4001),"")).isInstanceOf(IllegalArgumentException.class);
    }
    StatementValues value(String amount,String memo,String fitid) {return new StatementValues(date,new BigDecimal(amount),memo,fitid,"");}
}
