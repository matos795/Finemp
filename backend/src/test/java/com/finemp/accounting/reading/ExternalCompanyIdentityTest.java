package com.finemp.accounting.reading;

import static org.assertj.core.api.Assertions.assertThat;
import com.finemp.accounting.source.AccountingSourceType;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExternalCompanyIdentityTest {
    @Test void shouldRequireActualReferenceAndCnpjRatherThanOnlyEchoedCompanyCode() {
        var request = request();
        assertThat(new ExternalCompanyIdentity("synthetic", "26.262.626/0001-26").matches(request)).isTrue();
        assertThat(new ExternalCompanyIdentity("other", "26262626000126").matches(request)).isFalse();
        assertThat(new ExternalCompanyIdentity("synthetic", "27272727000127").matches(request)).isFalse();
        assertThat(new ExternalCompanyIdentity("synthetic", "invalid26262626000126").matches(request)).isFalse();
    }
    @Test void shouldRedactIdentityAndClaimContextFromTextRepresentation() {
        assertThat(new ExternalCompanyIdentity("synthetic-private-reference", "26262626000126").toString())
                .doesNotContain("synthetic-private-reference", "26262626000126");
    }
    @Test void shouldCompareQuestorReferencesAsPositiveIntegerCodesWithoutChangingObservedIdentity() {
        var padded = new AccountingReadRequest(UUID.randomUUID(), UUID.randomUUID(), AccountingSourceType.QUESTOR,
                "0057", "26262626000126", LocalDate.of(2026,7,1), LocalDate.of(2026,7,31));
        var observed = new ExternalCompanyIdentity("57", "26262626000126");
        assertThat(observed.matches(padded)).isTrue();
        assertThat(observed.externalReference()).isEqualTo("57");
        assertThat(new ExternalCompanyIdentity("58", observed.cnpj()).matches(padded)).isFalse();
        assertThat(new ExternalCompanyIdentity("57", "27272727000127").matches(padded)).isFalse();
        assertThat(new ExternalCompanyIdentity("0", observed.cnpj()).matches(padded)).isFalse();
        assertThat(new ExternalCompanyIdentity("2147483648", observed.cnpj()).matches(padded)).isFalse();
    }
    private AccountingReadRequest request() {
        return new AccountingReadRequest(UUID.randomUUID(), UUID.randomUUID(), AccountingSourceType.QUESTOR,
                "synthetic", "26262626000126", LocalDate.of(2026,7,1), LocalDate.of(2026,7,31));
    }
}

