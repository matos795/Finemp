package com.finemp.finance.entry;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EntryCommandJsonDecoderTest {
    private final EntryCommandJsonDecoder decoder=new EntryCommandJsonDecoder();
    @Test void exactTextMoneyAndIsoDatesMustBecomeCanonicalValuesWithoutRounding() {
        var request=decoder.decode(bytes(valid()));
        assertThat(request.values().nominalAmount()).isEqualByComparingTo("1.23");assertThat(request.expectedVersion()).isNull();
        assertThat(request.values().competence().toString()).isEqualTo("2026-07");assertThat(request.toString()).doesNotContain("Synthetic private description");
    }
    @Test void numericMoneyExponentSubcentavoAndNonpositiveCaptureAreOutsideTheLimitedContract() {
        for(var value:new String[]{"1.23","\"1e2\"","\"1.234\"","\"0\"","\"-1\"","\"NaN\"","\"999999999999999999.00\""})
            invalid(valid().replace("\"1.23\"",value));
    }
    @Test void unexpectedFieldsAndActorOrSettlementInjectionMustFailWithoutBeingIgnored() {
        for(var field:new String[]{"companyId","actorId","actualPaidAmount","unexpected"})invalid(valid().replace("\"description\":","\""+field+"\":\"injected\",\"description\":"));
        invalid(valid().replace("{\"values\":","{\"companyId\":\"injected\",\"values\":"));
    }
    @Test void duplicateKeysTrailingJsonMalformedShapesAndExcessiveDepthMustBeRejected() {
        invalid(valid().replace("\"description\":","\"description\":\"first\",\"description\":"));
        invalid(valid()+" {}");invalid("null");invalid("[]");invalid("{\"values\":[]}");
        invalid(valid().replace("\"description\":\"Synthetic private description\"","\"description\":{\"a\":{\"b\":{\"c\":{\"d\":1}}}}"));
    }
    @Test void versionMustBeANonnegativeIntegerAndInvalidEnumsOrReferencesMustFail() {
        var update=valid().replace("{\"values\":","{\"expectedVersion\":0,\"values\":");assertThat(decoder.decode(bytes(update)).expectedVersion()).isZero();
        for(var value:new String[]{"-1","1.5","\"0\"","9223372036854775808"})invalid(valid().replace("{\"values\":","{\"expectedVersion\":"+value+",\"values\":"));
        invalid(valid().replace("EXPENSE","expense"));invalid(valid().replace("2026-07-01","0000-07-01"));invalid(valid().replace("00000000-0000-0000-0000-000000000001","not-a-uuid"));
    }
    @Test void privateTextMustRejectNulUnpairedSurrogatesAndOversizeButKeepValidUnicode() {
        var escape=String.valueOf((char)92);
        for(var text:new String[]{escape+"u0000",escape+"uD800",escape+"uDC00",escape+"uD800a"})
            invalid(valid().replace("Synthetic private description","Safe "+text));
        invalid(valid().replace("Synthetic private description","x".repeat(4001)));
        var unicode=new String(Character.toChars(0x1F600));
        assertThat(decoder.decode(bytes(valid().replace("Synthetic private description","Safe "+unicode))).values().description()).contains(unicode);
    }
    private void invalid(String json) {assertThatThrownBy(()->decoder.decode(bytes(json))).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Corpo do comando financeiro inválido.");}
    private byte[] bytes(String value) {return value.getBytes(StandardCharsets.UTF_8);}
    static String valid() {return """
            {"values":{"direction":"EXPENSE","description":"Synthetic private description","nominalAmount":"1.23",
            "documentDate":"2026-07-01","entryDate":"2026-07-01","competence":"2026-07","dueDate":"2026-07-01",
            "bankAccountId":"00000000-0000-0000-0000-000000000001","categoryId":"00000000-0000-0000-0000-000000000002"}}
            """.trim();}
}
