package com.finemp.finance.entry;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MonthlyEntryJsonDecoderTest {
    final EntryCommandJsonDecoder decoder=new EntryCommandJsonDecoder();
    @Test void monthlyInputReusesCanonicalValuesAndRequiresAnExplicitBoundedIntegerQuantity() {
        var valid=EntryCommandJsonDecoderTest.valid().replace("{\"values\":","{\"quantity\":3,\"values\":");
        assertThat(decoder.decodeMonthly(bytes(valid)).quantity()).isEqualTo(3);assertThat(decoder.decodeMonthly(bytes(valid)).values().nominalAmount().toPlainString()).isEqualTo("1.23");
        for(var quantity:new String[]{"null","1","121","2.0","\"3\""})assertThatThrownBy(()->decoder.decodeMonthly(bytes(valid.replace("\"quantity\":3","\"quantity\":"+quantity)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->decoder.decodeMonthly(bytes(valid.replace("{\"quantity\":","{\"mode\":\"INSTALLMENTS\",\"quantity\":")))).isInstanceOf(IllegalArgumentException.class);
    }
    byte[] bytes(String text) {return text.getBytes(StandardCharsets.UTF_8);}
}
