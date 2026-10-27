package com.finemp.connector.completion;

import static org.assertj.core.api.Assertions.*;
import com.finemp.accounting.movement.AccountingMovementSide;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConnectorBatchDecoderTest {
    private final ConnectorBatchDecoder decoder = new ConnectorBatchDecoder(2);
    @Test void shouldPreserveDecimalPrecisionSignAndLiteralLabelsWithoutInterpretingThem() {
        var batch = decode(batch(row("-900719925474.0999")));
        assertThat(batch.movements()).hasSize(1);
        var movement = batch.movements().getFirst();
        assertThat(movement.amount()).isEqualByComparingTo("-900719925474.0999");
        assertThat(movement.side()).isEqualTo(AccountingMovementSide.CREDIT);
        assertThat(movement.accountDescription()).isEqualTo("0 / N/A / (-) Verificar");
        assertThat(batch.observedIdentity().cnpj()).isEqualTo("47.474.747/0001-47");
        assertThat(batch.toString()).doesNotContain("Verificar", "47474747");
    }
    @Test void shouldAcceptEmptyBatchesNullableHistoryAndAnExactDecimalString() {
        assertThat(decode(batch("")).movements()).isEmpty();
        var parsed = decode(batch(row("\"1.23000\"")));
        assertThat(parsed.movements().getFirst().amount()).isEqualByComparingTo("1.23000");
        assertThat(parsed.movements().getFirst().history()).isNull();
    }
    @Test void shouldRejectUnknownDuplicateTrailingAndWrongShapeFieldsWithoutExposingTheInput() {
        var valid = batch(row("1"));
        for (var invalid : new String[]{
                valid + "{}",
                valid.replace("\"observedIdentity\":", "\"status\":\"SUCCEEDED\",\"observedIdentity\":"),
                valid.replace("\"side\":\"CREDIT\"", "\"side\":\"CREDIT\",\"side\":\"DEBIT\""),
                valid.replace("\"amount\":1", "\"amount\":{\"private\":\"synthetic detail\"}"),
                valid.replace("\"movements\":[", "\"movements\":{},\"ignored\":["),
                "null", "[]"}) {
            assertThatThrownBy(() -> decode(invalid)).isInstanceOf(InvalidConnectorBatchException.class)
                    .hasMessage("Invalid connector batch.").hasNoCause();
        }
    }
    @Test void shouldStopBeforeMaterializingOrParsingTheRecordBeyondTheBudget() {
        assertThat(decode(batch(row("1") + "," + row("2"))).movements()).hasSize(2);
        String overBudget = batch(row("1") + "," + row("2") + ",{]");
        assertThatThrownBy(() -> decode(overBudget)).isInstanceOf(ConnectorBatchResourceLimitException.class).hasNoCause();
    }
    @Test void shouldBoundNumericAndStringTokensIndependentlyOfTheOuterByteBudget() {
        assertThatThrownBy(() -> decode(batch(row("1".repeat(65)))))
                .isInstanceOf(ConnectorBatchResourceLimitException.class).hasNoCause();
        assertThatThrownBy(() -> decode(batch(row("1").replace("0 / N/A / (-) Verificar", "x".repeat(20000)))))
                .isInstanceOf(ConnectorBatchResourceLimitException.class).hasNoCause();
    }
    @Test void shouldRejectMissingIdentityOrRequiredMovementFieldsAndInvalidDates() {
        for (var invalid : new String[]{
                "{\"movements\":[]}",
                batch(row("1")).replace("\"cnpj\":\"47.474.747/0001-47\"", "\"unexpected\":\"synthetic\""),
                batch(row("1")).replace("\"accountKey\":\"1.100\",", ""),
                batch(row("1")).replace("2026-07-01", "2026-02-30")}) {
            assertThatThrownBy(() -> decode(invalid)).isInstanceOf(InvalidConnectorBatchException.class).hasNoCause();
        }
    }
    @Test void shouldAllowOnlySanitizedSourceFailureCodesWithoutMessagesOrCounters() {
        assertThat(decoder.decodeFailure(input("{\"code\":\"READ_FAILED\"}"))).isEqualTo(com.finemp.accounting.sync.SyncFailureCode.READ_FAILED);
        assertThat(decoder.decodeFailure(input("{\"code\":\"RESOURCE_LIMIT\"}"))).isEqualTo(com.finemp.accounting.sync.SyncFailureCode.RESOURCE_LIMIT);
        for (var value : new String[]{"{\"code\":\"PUBLICATION_FAILED\"}", "{\"code\":\"READ_FAILED\",\"message\":\"synthetic private detail\"}",
                "{\"code\":\"READ_FAILED\",\"code\":\"INVALID_PAYLOAD\"}", "{\"code\":\"READ_FAILED\"}{}"})
            assertThatThrownBy(() -> decoder.decodeFailure(input(value))).isInstanceOf(InvalidConnectorBatchException.class).hasNoCause();
    }
    private ByteArrayInputStream input(String json) { return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)); }
    private ConnectorBatch decode(String json) { return decoder.decode(input(json)); }
    private String batch(String rows) {
        return "{\"observedIdentity\":{\"externalReference\":\"synthetic\",\"cnpj\":\"47.474.747/0001-47\"},\"movements\":[" + rows + "]}";
    }
    private String row(String amount) {
        return """
                {"sourceEntryKey":"synthetic","accountingDate":"2026-07-01","externalAccountId":"100","accountKey":"1.100","accountDescription":"0 / N/A / (-) Verificar","side":"CREDIT","amount":%s,"history":null}
                """.formatted(amount).trim();
    }
}
