package com.finemp.accounting.fileimport;

import static org.assertj.core.api.Assertions.*;

import com.finemp.accounting.movement.AccountingMovementSide;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AnalyticalLedgerHtmlLayoutProfileTest {

    @Test
    void shouldPreviewTheEvidenceBackedAnalyticalLedgerHtmlExport() {
        var preview = new AnalyticalLedgerHtmlLayoutProfile().preview(validFixture());

        assertThat(preview.layoutProfile()).isEqualTo("analytical-ledger-html-v1");
        assertThat(preview.format()).isEqualTo(AccountingFileFormat.HTML_XLS);
        assertThat(preview.periodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(preview.periodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(preview.observedAccountCount()).isEqualTo(2);
        assertThat(preview.movementCount()).isEqualTo(3);
        assertThat(preview.invalidRowCount()).isZero();
        assertThat(preview.errors()).isEmpty();

        assertThat(preview.sampleRows()).hasSize(3);
        var debit = preview.sampleRows().getFirst();
        assertThat(debit.sourceAccountId()).isEqualTo("111010001");
        assertThat(debit.accountDescription()).isEqualTo("CAIXA SINTETICO");
        assertThat(debit.accountingDate()).isEqualTo(LocalDate.of(2026, 1, 13));
        assertThat(debit.sourceEntryKey()).isEqualTo("0/100");
        assertThat(debit.side()).isEqualTo(AccountingMovementSide.DEBIT);
        assertThat(debit.amount()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(debit.reportedBalance()).isEqualByComparingTo(new BigDecimal("1234.56"));

        var credit = preview.sampleRows().get(1);
        assertThat(credit.side()).isEqualTo(AccountingMovementSide.CREDIT);
        assertThat(credit.amount()).isEqualByComparingTo(new BigDecimal("10.01"));
    }

    @Test
    void shouldPreserveInvalidMovementRowsAsSanitizedPreviewErrors() {
        var preview = new AnalyticalLedgerHtmlLayoutProfile().preview(invalidMovementFixture());

        assertThat(preview.movementCount()).isZero();
        assertThat(preview.invalidRowCount()).isEqualTo(1);
        assertThat(preview.errors()).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.code()).isEqualTo("MOVEMENT_SIDE_AMBIGUOUS");
                    assertThat(issue.sourceRow()).isPositive();
                    assertThat(issue.message()).isEqualTo("A linha possui débito e crédito ao mesmo tempo.");
                });
        assertThat(preview.sampleRows()).isEmpty();
    }

    @Test
    void shouldRejectMalformedBrazilianMoneyInsteadOfNormalizingIt() {
        byte[] malformed = """
                <html><title>Razão Analítico</title><body><table>
                <tr><td>Consolidação: Empresa</td><td></td><td></td><td></td><td></td><td></td><td></td><td>Período: 01/01/2026 a 31/01/2026</td></tr>
                <tr><td>Data</td><td>Histórico</td><td></td><td>Lote/Lcto.</td><td></td><td>Débito</td><td>Crédito</td><td>Saldo</td></tr>
                <tr><td>Conta: 111010001</td><td>Red.: 1-9  CAIXA SINTETICO</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>15/01/2026</td><td>VALOR INVALIDO</td><td></td><td>0/250</td><td></td><td>1.234,5.6</td><td></td><td>0,00</td></tr>
                </table></body></html>
                """.getBytes(StandardCharsets.ISO_8859_1);

        var preview = new AnalyticalLedgerHtmlLayoutProfile().preview(malformed);

        assertThat(preview.movementCount()).isZero();
        assertThat(preview.invalidRowCount()).isEqualTo(1);
        assertThat(preview.errors()).singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("INVALID_MONEY"));
    }

    @Test
    void shouldPreserveMovementLikeRowsWithMalformedDates() {
        byte[] malformed = """
                <html><title>Razão Analítico</title><body><table>
                <tr><td>Consolidação: Empresa</td><td></td><td></td><td></td><td></td><td></td><td></td><td>Período: 01/01/2026 a 31/01/2026</td></tr>
                <tr><td>Data</td><td>Histórico</td><td></td><td>Lote/Lcto.</td><td></td><td>Débito</td><td>Crédito</td><td>Saldo</td></tr>
                <tr><td>Conta: 111010001</td><td>Red.: 1-9  CAIXA SINTETICO</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>31/02/2026</td><td>DATA INVALIDA</td><td></td><td>0/300</td><td></td><td>1,00</td><td></td><td>1,00</td></tr>
                <tr><td>2026-01-15</td><td>FORMATO INVALIDO</td><td></td><td>0/301</td><td></td><td></td><td>2,00</td><td>-1,00</td></tr>
                </table></body></html>
                """.getBytes(StandardCharsets.ISO_8859_1);

        var preview = new AnalyticalLedgerHtmlLayoutProfile().preview(malformed);

        assertThat(preview.movementCount()).isZero();
        assertThat(preview.invalidRowCount()).isEqualTo(2);
        assertThat(preview.errors())
                .extracting(GeneralLedgerPreviewIssue::code)
                .containsExactly("INVALID_DATE", "INVALID_DATE");
    }

    @Test
    void shouldFailClosedWhenTheFileIsNotTheSupportedHtmlLedgerLayout() {
        byte[] unrelated = "<html><body><table><tr><td>Outro relatório</td></tr></table></body></html>"
                .getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> new AnalyticalLedgerHtmlLayoutProfile().preview(unrelated))
                .isInstanceOf(AccountingFilePreviewException.class)
                .hasMessage("Formato de Razão não reconhecido.");
    }

    private byte[] validFixture() {
        return """
                <html><title>Razão Analítico</title><body><table>
                <tr><td>EMPRESA SINTETICA</td><td></td><td></td><td></td><td></td><td></td><td>Página:</td><td>1</td></tr>
                <tr><td>Contabilidade</td><td></td><td>Razão Analítico</td><td></td><td></td><td></td><td>Data:</td><td>01/10/2026</td></tr>
                <tr><td></td><td></td><td></td><td></td><td></td><td></td><td>Hora:</td><td>10:00:00</td></tr>
                <tr><td>Consolidação: Empresa</td><td></td><td></td><td></td><td></td><td></td><td></td><td>Período: 01/01/2026 a 30/09/2026</td></tr>
                <tr><td></td></tr>
                <tr><td>Data</td><td>Histórico</td><td></td><td>Lote/Lcto.</td><td></td><td>Débito</td><td>Crédito</td><td>Saldo</td></tr>
                <tr><td>Conta: 111010001</td><td>Red.: 1-9  CAIXA SINTETICO</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>13/01/2026</td><td>HISTORICO SINTETICO A</td><td></td><td>0/100</td><td></td><td>1.234,56</td><td></td><td>1.234,56</td></tr>
                <tr><td>14/01/2026</td><td>HISTORICO SINTETICO B</td><td></td><td>0/101</td><td></td><td></td><td>10,01</td><td>1.224,55</td></tr>
                <tr><td></td><td></td><td></td><td></td><td>Total da Conta:</td><td>1.234,56</td><td>10,01</td><td>1.224,55</td></tr>
                <tr><td>Conta: 311010001</td><td>Red.: 9-9  RECEITA SINTETICA</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>30/09/2026</td><td>HISTORICO SINTETICO C</td><td></td><td>0/200</td><td></td><td></td><td>2.000,00</td><td>-2.000,00</td></tr>
                <tr><td></td><td></td><td></td><td></td><td>Total da Conta:</td><td>0,00</td><td>2.000,00</td><td>-2.000,00</td></tr>
                </table></body></html>
                """.getBytes(StandardCharsets.ISO_8859_1);
    }

    private byte[] invalidMovementFixture() {
        return """
                <html><title>Razão Analítico</title><body><table>
                <tr><td>Consolidação: Empresa</td><td></td><td></td><td></td><td></td><td></td><td></td><td>Período: 01/01/2026 a 31/01/2026</td></tr>
                <tr><td>Data</td><td>Histórico</td><td></td><td>Lote/Lcto.</td><td></td><td>Débito</td><td>Crédito</td><td>Saldo</td></tr>
                <tr><td>Conta: 111010001</td><td>Red.: 1-9  CAIXA SINTETICO</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>13/01/2026</td><td>INVALIDA</td><td></td><td>0/100</td><td></td><td>1,00</td><td>1,00</td><td>0,00</td></tr>
                </table></body></html>
                """.getBytes(StandardCharsets.ISO_8859_1);
    }
}
