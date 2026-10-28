package com.finemp.finance.entry;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EntrySettlementJsonDecoderTest {
    final EntryCommandJsonDecoder decoder=new EntryCommandJsonDecoder();
    @Test void settlementInputMustCarryOnlyCanonicalPaymentIntentAndExpectedVersion() {
        var request=decoder.decodeSettlement(bytes(valid()));
        assertThat(request.expectedVersion()).isZero();assertThat(request.values().paidAmount()).isEqualByComparingTo("110.50");
        assertThat(request.values().paymentDate()).isEqualTo(LocalDate.of(2026,7,10));
        assertThat(request.toString()).doesNotContain("110.50","00000000-0000-0000-0000-000000000001");
    }
    @Test void reversalMustAcceptOnlyANonnegativeIntegralExpectedVersion() {
        assertThat(decoder.decodeReversal(bytes("{\"expectedVersion\":0}"))).isEqualTo(0L);
        for(var json:new String[]{"{}","null","[]","{\"expectedVersion\":null}","{\"expectedVersion\":-1}","{\"expectedVersion\":\"0\"}",
                "{\"expectedVersion\":0.0}","{\"expectedVersion\":9223372036854775808}","{\"expectedVersion\":0,\"values\":null}"})
            assertThatThrownBy(()->decoder.decodeReversal(bytes(json))).isInstanceOf(IllegalArgumentException.class).hasMessage("Corpo do comando financeiro inválido.");
    }
    @Test void settlementMustRejectRawMoneyInvalidDateBankAndMissingVersion() {
        for(var value:new String[]{"110.50","\"0\"","\"-1\"","\"1.001\"","\"1e2\""})invalid(valid().replace("\"110.50\"",value));
        invalid(valid().replace("2026-07-10","0000-07-10"));invalid(valid().replace("2026-07-10","10000-07-10"));
        invalid(valid().replace("00000000-0000-0000-0000-000000000001","invalid"));invalid(valid().replace("\"expectedVersion\":0,",""));
        invalid(valid().replace("\"expectedVersion\":0","\"expectedVersion\":null"));
    }
    @Test void paymentCannotMutateNominalClassifiersOwnershipOrOtherEntryFields() {
        for(var name:new String[]{"nominalAmount","actualPaidAmount","categoryId","partnerId","resultCenterId","direction","description","actorId","companyId"})
            invalid(valid().replace("\"paymentDate\":","\""+name+"\":\"injected\",\"paymentDate\":"));
        invalid(valid().replace("{\"expectedVersion\":","{\"companyId\":\"injected\",\"expectedVersion\":"));
    }
    @Test void duplicateKeysTrailingDataAndNonObjectValuesMustNotBeAccepted() {
        invalid(valid().replace("\"paidAmount\":","\"paidAmount\":\"1.00\",\"paidAmount\":"));invalid(valid()+" {}");
        invalid("{\"expectedVersion\":0,\"values\":[]}");
        assertThatThrownBy(()->decoder.decodeReversal(bytes("{\"expectedVersion\":0,\"expectedVersion\":1}"))).isInstanceOf(IllegalArgumentException.class);
    }
    void invalid(String json) {assertThatThrownBy(()->decoder.decodeSettlement(bytes(json))).isInstanceOf(IllegalArgumentException.class).hasMessage("Corpo do comando financeiro inválido.");}
    byte[] bytes(String value) {return value.getBytes(StandardCharsets.UTF_8);}
    static String valid() {return "{\"expectedVersion\":0,\"values\":{\"paymentDate\":\"2026-07-10\",\"paidAmount\":\"110.50\",\"bankAccountId\":\"00000000-0000-0000-0000-000000000001\"}}";}
}
