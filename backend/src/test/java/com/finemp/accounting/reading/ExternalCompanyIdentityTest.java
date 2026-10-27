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
    private AccountingReadRequest request() {
        return new AccountingReadRequest(UUID.randomUUID(), UUID.randomUUID(), AccountingSourceType.QUESTOR,
                "synthetic", "26262626000126", LocalDate.of(2026,7,1), LocalDate.of(2026,7,31));
    }
}

