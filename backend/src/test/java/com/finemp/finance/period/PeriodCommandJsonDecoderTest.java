package com.finemp.finance.period;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PeriodCommandJsonDecoderTest {
    final PeriodCommandJsonDecoder decoder=new PeriodCommandJsonDecoder();
    @Test void canonicalPreconditionsAndReasonMustKeepExactValuesAndRedactPrivateText() {
        assertThat(decode("{\"expectedVersion\":null}",false).expectedVersion()).isNull();
        var request=decode("{\"expectedVersion\":0,\"reason\":\"Revisão autorizada\"}",true);
        assertThat(request.expectedVersion()).isZero();assertThat(request.reason()).isEqualTo("Revisão autorizada");assertThat(request.toString()).doesNotContain(request.reason());
    }
    @Test void parserMustRejectAmbiguousVersionsDuplicatesTrailingDocumentsAndUnownedFields() {
        for(var body:java.util.List.of("{}","{\"expectedVersion\":0,\"expectedVersion\":1}","{\"expectedVersion\":0} {}","{\"expectedVersion\":\"0\"}","{\"expectedVersion\":0.0}","{\"expectedVersion\":-1}","{\"expectedVersion\":9223372036854775808}","{\"expectedVersion\":null,\"companyId\":\"other\"}"))
            assertThatThrownBy(()->decode(body,false)).isInstanceOf(IllegalArgumentException.class);
        for(var body:java.util.List.of("{\"expectedVersion\":null,\"reason\":\"Valid\"}","{\"expectedVersion\":0}","{\"expectedVersion\":0,\"reason\":\" \"}","{\"expectedVersion\":0,\"reason\":false}","{\"expectedVersion\":0,\"reason\":\""+"x".repeat(1001)+"\"}"))
            assertThatThrownBy(()->decode(body,true)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void monthMustBeExactIsoWithinTheSupportedFinancialCalendar() {
        assertThat(PeriodCommandJsonDecoder.period("9999-12").toString()).isEqualTo("9999-12");
        for(var month:java.util.List.of("0000-07","2026-7","2026-13","+2026-07","10000-07"," 2026-07"))assertThatThrownBy(()->PeriodCommandJsonDecoder.period(month)).isInstanceOf(IllegalArgumentException.class);
    }
    PeriodCommandJsonDecoder.Request decode(String body,boolean reopen) {return decoder.decode(body.getBytes(StandardCharsets.UTF_8),reopen);}
}
