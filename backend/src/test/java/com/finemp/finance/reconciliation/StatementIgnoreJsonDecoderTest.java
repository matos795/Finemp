package com.finemp.finance.reconciliation;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StatementIgnoreJsonDecoderTest {
    final StatementIgnoreJsonDecoder decoder=new StatementIgnoreJsonDecoder();
    @Test void acceptsOnlyOneNonnegativeIntegerVersion(){
        assertThat(decoder.decode("{\"expectedVersion\":2}".getBytes(StandardCharsets.UTF_8))).isEqualTo(2);
        for(String invalid:new String[]{"{}","{\"expectedVersion\":-1}","{\"expectedVersion\":1.0}","{\"expectedVersion\":0,\"expectedVersion\":1}","{\"expectedVersion\":0,\"extra\":true}"})assertThatThrownBy(()->decoder.decode(invalid.getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }
}
