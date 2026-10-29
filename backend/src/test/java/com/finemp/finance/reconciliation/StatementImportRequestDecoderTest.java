package com.finemp.finance.reconciliation;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StatementImportRequestDecoderTest {
    final StatementImportRequestDecoder decoder=new StatementImportRequestDecoder();
    static String body="{\"format\":\"CSV\",\"separator\":\"SEMICOLON\",\"fileBase64\":\"SGVsbG8=\",\"fileSha256\":\""+"a".repeat(64)+"\",\"selectedRows\":[5,2],\"accountConfirmed\":true,\"currencyConfirmed\":true,\"warningsConfirmed\":true}";
    @Test void strictlyDecodesAndCanonicalizesSelection(){var value=decoder.decode(body.getBytes(StandardCharsets.UTF_8));assertThat(value.selectedRows()).containsExactly(2,5);assertThat(value.file()).containsExactly("Hello".getBytes(StandardCharsets.UTF_8));assertThat(value.toString()).doesNotContain("Hello","SGVsbG8");}
    @Test void rejectsMissingAcknowledgementUnknownFieldsDuplicateKeysAndRepeatedRows(){
        for(var invalid:new String[]{body.replace("\"warningsConfirmed\":true","\"warningsConfirmed\":false"),body.replace("\"format\":\"CSV\"","\"format\":\"CSV\",\"extra\":1"),body.replace("\"format\":\"CSV\"","\"format\":\"CSV\",\"format\":\"CSV\""),body.replace("[5,2]","[5,5]"),body.replace("SGVsbG8=","not*base64")})assertThatThrownBy(()->decoder.decode(invalid.getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }
}
