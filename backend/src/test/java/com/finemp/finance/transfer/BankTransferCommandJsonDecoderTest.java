package com.finemp.finance.transfer;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BankTransferCommandJsonDecoderTest {
    final BankTransferCommandJsonDecoder decoder=new BankTransferCommandJsonDecoder();
    final String valid="{\"values\":{\"originBankAccountId\":\"00000000-0000-0000-0000-000000000001\",\"destinationBankAccountId\":\"00000000-0000-0000-0000-000000000002\",\"transferDate\":\"2026-07-01\",\"amount\":\"1.23\",\"description\":\" Synthetic \"}}";
    @Test void canonicalInputMustPreserveExactAmountAndOptionalTrimmedDescription() {
        assertThat(decode(valid).amount().toPlainString()).isEqualTo("1.23");assertThat(decode(valid).description()).isEqualTo("Synthetic");
        assertThat(decode(valid.replace(",\"description\":\" Synthetic \"","")).description()).isEmpty();
    }
    @Test void moneyMustRemainPositiveTextInExactCentsWithoutNumericCoercionOrExponent() {
        for(var value:new String[]{"1.23","\"0\"","\"-1\"","\"1.001\"","\"1e2\"","\"100000000000000000.00\"","null"})reject(valid.replace("\"amount\":\"1.23\"","\"amount\":"+value));
    }
    @Test void unknownDuplicateTrailingAndSpoofedContextFieldsMustBeRejected() {
        reject(valid.replace("\"values\":","\"actorId\":\"private\",\"values\":"));
        reject(valid.replace("\"amount\":\"1.23\"","\"amount\":\"1.23\",\"amount\":\"2.00\""));
        reject(valid.replace("\"amount\":\"1.23\"","\"amount\":\"1.23\",\"companyId\":\"private\""));reject(valid+"{}");
        reject(valid.replace("\"values\":","\"expectedVersion\":0,\"values\":"));
    }
    @Test void accountIdentityDatesAndUnsupportedTextMustBeRejectedWithFixedRedactedError() {
        reject(valid.replace("00000000-0000-0000-0000-000000000001","1-1-1-1-1"));
        reject(valid.replace("00000000-0000-0000-0000-000000000002","00000000-0000-0000-0000-000000000001"));
        reject(valid.replace("2026-07-01","2026-02-30"));reject(valid.replace(" Synthetic ","\\u0000private"));reject(valid.replace(" Synthetic ","\\ud800private"));
        reject(valid.replace(" Synthetic ","x".repeat(256)));
    }
    @Test void absentDeepAndOversizedBodiesMustBeRejectedBeforeUnboundedParsing() {
        assertThatThrownBy(()->decoder.decode(null)).isInstanceOf(IllegalArgumentException.class);reject("");reject("[".repeat(10)+"]".repeat(10));reject(" ".repeat(65537));
    }
    BankTransferValues decode(String value) {return decoder.decode(value.getBytes(StandardCharsets.UTF_8));}
    void reject(String body) {assertThatThrownBy(()->decode(body)).isInstanceOf(IllegalArgumentException.class).hasMessage("Corpo do comando de transferência inválido.");}
}
