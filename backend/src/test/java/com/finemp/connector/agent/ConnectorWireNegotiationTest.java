package com.finemp.connector.agent;

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
import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.reading.ExternalCompanyIdentity;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.SyncRunMode;
import com.finemp.connector.protocol.ConnectorCanonicalV2Codec;
import com.finemp.connector.work.ConnectorWorkResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ConnectorWireNegotiationTest {
    private final UUID source = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-07-01T12:00:00Z");
    private final AccountingReadRequest request = new AccountingReadRequest(
            source,
            UUID.randomUUID(),
            AccountingSourceType.QUESTOR,
            "0057",
            "51515151000151",
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31));

    @Test
    void shouldDowngradeToV1WhenAnOlderServerDoesNotAdvertiseNegotiationFields() {
        var codec = new ConnectorProtocolCodec(100);
        var mapper = JsonMapper.builder().build();
        var advertised = work(Set.of(1), Set.of(AccountingDataset.MOVEMENTS));
        ObjectNode root = (ObjectNode) mapper.valueToTree(advertised);
        root.remove("acceptedContractVersions");
        root.remove("acceptedDatasets");

        var decoded = codec.work(mapper.writeValueAsBytes(root), source);

        assertThat(decoded.acceptedContractVersions()).containsExactly(1);
        assertThat(decoded.acceptedDatasets()).containsExactly(AccountingDataset.MOVEMENTS);
    }

    @Test
    void shouldSendV2OnlyWhenTheLeaseAdvertisesVersionAndEveryDataset() {
        var codec = new ConnectorProtocolCodec(100);
        var batch = batch();

        assertThatThrownBy(() -> codec.batchV2(
                        work(Set.of(1), Set.of(AccountingDataset.MOVEMENTS)),
                        batch))
                .isInstanceOf(ConnectorClientException.class)
                .hasMessage("Connector client: PROTOCOL");

        var negotiated = work(
                Set.of(1, 2),
                Set.of(AccountingDataset.CHART, AccountingDataset.MOVEMENTS));
        byte[] payload = codec.batchV2(negotiated, batch);

        assertThat(new ConnectorCanonicalV2Codec(100).decode(payload)).isEqualTo(batch);
    }

    @Test
    void shouldRejectUnknownDuplicateOrEmptyNegotiationOffers() {
        var codec = new ConnectorProtocolCodec(100);
        var mapper = JsonMapper.builder().build();
        ObjectNode valid = (ObjectNode) mapper.valueToTree(work(
                Set.of(1, 2),
                Set.of(AccountingDataset.CHART, AccountingDataset.MOVEMENTS)));

        var duplicateVersion = valid.deepCopy();
        duplicateVersion.putArray("acceptedContractVersions").add(1).add(2).add(2);

        var emptyVersions = valid.deepCopy();
        emptyVersions.putArray("acceptedContractVersions");

        var unknownVersion = valid.deepCopy();
        unknownVersion.putArray("acceptedContractVersions").add(1).add(3);

        var unknownDataset = valid.deepCopy();
        unknownDataset.putArray("acceptedDatasets").add("CHART").add("UNKNOWN");

        for (ObjectNode invalid : List.of(
                duplicateVersion,
                emptyVersions,
                unknownVersion,
                unknownDataset)) {
            assertThatThrownBy(() -> codec.work(mapper.writeValueAsBytes(invalid), source))
                    .isInstanceOf(ConnectorClientException.class)
                    .hasMessage("Connector client: PROTOCOL");
        }
    }

    private ConnectorWorkResponse work(Set<Integer> versions, Set<AccountingDataset> datasets) {
        return new ConnectorWorkResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                SyncRunMode.INITIAL,
                now.plusSeconds(300),
                now,
                request,
                versions,
                datasets);
    }

    private CanonicalAccountingBatch batch() {
        var account = new CanonicalAccountingAccount(
                "100",
                "1.1",
                "Synthetic account",
                new SourceAccountCodes(
                        "RAW_TYPE",
                        "RAW_CALC",
                        "RAW_NATURE",
                        "RAW_CONTROL",
                        "RAW_SHOW",
                        null,
                        null));
        return new CanonicalAccountingBatch(
                CanonicalAccountingBatch.VERSION,
                new ExternalCompanyIdentity("0057", request.expectedCompanyCnpj()),
                new SourceCapabilities(
                        Set.of(AccountingDataset.CHART, AccountingDataset.MOVEMENTS),
                        AmountSignPolicy.NON_NEGATIVE_WITH_SIDE,
                        true),
                new CanonicalChartSnapshot(now, List.of(account)),
                new CanonicalEntriesDataset(
                        request.startDate(),
                        request.endDate(),
                        List.of(new CanonicalAccountingEntry(
                                "entry-1",
                                LocalDate.of(2026, 7, 15),
                                "100",
                                null,
                                new BigDecimal("1.0000"),
                                "1",
                                null)),
                        List.of()),
                null,
                null,
                null,
                null,
                null);
    }
}
