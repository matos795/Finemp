package com.finemp.connector.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.canonical.v2.AmountSignPolicy;
import com.finemp.accounting.canonical.v2.CanonicalAccountingAccount;
import com.finemp.accounting.canonical.v2.CanonicalAccountingBatch;
import com.finemp.accounting.canonical.v2.CanonicalAccountingEntry;
import com.finemp.accounting.canonical.v2.CanonicalChartSnapshot;
import com.finemp.accounting.canonical.v2.CanonicalEntriesDataset;
import com.finemp.accounting.canonical.v2.SourceAccountCodes;
import com.finemp.accounting.canonical.v2.SourceCapabilities;
import com.finemp.accounting.reading.ExternalCompanyIdentity;
import com.finemp.connector.completion.ConnectorBatchResourceLimitException;
import com.finemp.connector.completion.InvalidConnectorBatchException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConnectorCanonicalV2CodecTest {

    @Test
    void shouldRoundTripCanonicalV2WithoutInterpretingSourceCodesOrMoney() {
        var codec = new ConnectorCanonicalV2Codec(20);
        var batch = batch(List.of(entry("entry-1", "999999999999.9999")));

        byte[] encoded = codec.encode(batch);
        var decoded = codec.decode(encoded);

        assertThat(decoded).isEqualTo(batch);
        assertThat(decoded.entries().entries().getFirst().amount())
                .isEqualByComparingTo("999999999999.9999");
        assertThat(decoded.chart().accounts().getFirst().codes().accountType())
                .isEqualTo("SYNTHETIC_RAW");
        assertThat(new String(encoded, StandardCharsets.UTF_8))
                .contains("999999999999.9999")
                .doesNotContain("E+");
    }

    @Test
    void shouldRejectUnknownFieldsWrongVersionAndTrailingPayloadWithoutEchoingInput() {
        var codec = new ConnectorCanonicalV2Codec(20);
        String valid = new String(codec.encode(batch(List.of(entry("entry-1", "1.0000")))), StandardCharsets.UTF_8);

        for (String invalid : List.of(
                valid.replaceFirst("\\{", "{\"privateUnexpected\":\"secret-detail\","),
                valid.replace("\"contractVersion\":2", "\"contractVersion\":3"),
                valid + "{}")) {
            assertThatThrownBy(() -> codec.decode(invalid.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(InvalidConnectorBatchException.class)
                    .hasMessage("Invalid connector batch.")
                    .hasNoCause()
                    .hasMessageNotContaining("secret-detail");
        }
    }

    @Test
    void shouldApplyAggregateRecordAndByteBudgets() {
        var codec = new ConnectorCanonicalV2Codec(3);
        var entries = new ArrayList<com.finemp.accounting.canonical.v2.CanonicalAccountingEntry>();
        entries.add(entry("entry-1", "1.0000"));
        entries.add(entry("entry-2", "1.0000"));
        entries.add(entry("entry-3", "1.0000"));

        assertThatThrownBy(() -> codec.encode(batch(entries)))
                .isInstanceOf(ConnectorBatchResourceLimitException.class)
                .hasNoCause();

        byte[] tooLarge = new byte[16 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> codec.decode(tooLarge))
                .isInstanceOf(ConnectorBatchResourceLimitException.class)
                .hasNoCause();
    }

    private CanonicalAccountingBatch batch(List<CanonicalAccountingEntry> entries) {
        var account = new CanonicalAccountingAccount(
                "100",
                "1.1",
                "Synthetic account",
                new SourceAccountCodes(
                        "SYNTHETIC_RAW",
                        "CALC_RAW",
                        "NATURE_RAW",
                        "CONTROL_RAW",
                        "SHOW_RAW",
                        null,
                        null));
        return new CanonicalAccountingBatch(
                CanonicalAccountingBatch.VERSION,
                new ExternalCompanyIdentity("0057", "51515151000151"),
                new SourceCapabilities(
                        Set.of(AccountingDataset.CHART, AccountingDataset.MOVEMENTS),
                        AmountSignPolicy.NON_NEGATIVE_WITH_SIDE,
                        true),
                new CanonicalChartSnapshot(
                        Instant.parse("2026-07-31T23:59:59Z"),
                        List.of(account)),
                new CanonicalEntriesDataset(
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31),
                        entries,
                        List.of()),
                null,
                null,
                null,
                null,
                null);
    }

    private CanonicalAccountingEntry entry(String key, String amount) {
        return new CanonicalAccountingEntry(
                key,
                LocalDate.of(2026, 7, 15),
                "100",
                null,
                new BigDecimal(amount),
                "1",
                "Synthetic history");
    }
}
