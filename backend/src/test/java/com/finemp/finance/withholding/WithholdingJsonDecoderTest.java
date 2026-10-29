package com.finemp.finance.withholding;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WithholdingJsonDecoderTest {
    final WithholdingJsonDecoder decoder=new WithholdingJsonDecoder();
    static String body="{\"entryVersion\":0,\"values\":{\"taxCode\":\"IRRF\",\"treatment\":\"PAYABLE\",\"amount\":\"1.23\"}}";
    @Test void explicitValuesAndPreconditionsMustRemainExactWithoutRates() {
        var request=decode(body,WithholdingAction.CREATED);assertThat(request.entryVersion()).isZero();assertThat(request.values().amount()).isEqualByComparingTo("1.23");assertThat(request.toString()).doesNotContain("IRRF");
        assertThat(decode("{\"entryVersion\":0,\"expectedVersion\":1}",WithholdingAction.DELETED).expectedVersion()).isEqualTo(1);
    }
    @Test void parserMustRejectAmbiguousMoneyVersionsOwnershipAndDuplicates() {
        for(var invalid:java.util.List.of(body.replace("\"1.23\"","1.23"),body.replace("1.23","1.005"),body.replace("\"PAYABLE\"","0"),body.replace("\"entryVersion\":0","\"entryVersion\":\"0\""),body.replace("\"entryVersion\":0","\"entryVersion\":0,\"entryVersion\":1"),body.replace("\"entryVersion\":0","\"entryVersion\":0,\"actor\":\"other\""),body+" {}"))assertThatThrownBy(()->decode(invalid,WithholdingAction.CREATED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->decode(body,WithholdingAction.DELETED)).isInstanceOf(IllegalArgumentException.class);
    }
    WithholdingJsonDecoder.Request decode(String value,WithholdingAction action) {return decoder.decode(value.getBytes(StandardCharsets.UTF_8),action);}
}
