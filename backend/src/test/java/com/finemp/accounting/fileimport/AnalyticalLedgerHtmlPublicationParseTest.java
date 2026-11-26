package com.finemp.accounting.fileimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.accounting.movement.AccountingMovementSide;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AnalyticalLedgerHtmlPublicationParseTest {

    @Test
    void shouldExposeEveryObservedAccountAndMovementForServerSideConfirmation() {
        var parsed = new AnalyticalLedgerHtmlLayoutProfile().parse(fixture());

        assertThat(parsed.periodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(parsed.periodEnd()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.accounts()).hasSize(3);
        assertThat(parsed.movements()).hasSize(2);

        assertThat(parsed.accounts())
                .extracting(GeneralLedgerObservedAccount::sourceAccountId)
                .containsExactly("111010001", "211010001", "311010001");

        assertThat(parsed.movements().getFirst().sourceAccountId()).isEqualTo("111010001");
        assertThat(parsed.movements().getFirst().side()).isEqualTo(AccountingMovementSide.DEBIT);
        assertThat(parsed.movements().get(1).sourceAccountId()).isEqualTo("311010001");
        assertThat(parsed.movements().get(1).side()).isEqualTo(AccountingMovementSide.CREDIT);

        // Conta sem movimento ainda pertence ao universo observado do arquivo.
        assertThat(parsed.accounts().get(1).description()).isEqualTo("FORNECEDORES SINTETICO");
    }

    @Test
    void previewMustRemainASampleOfTheSameIntegralParse() {
        var profile = new AnalyticalLedgerHtmlLayoutProfile();
        var parsed = profile.parse(fixture());
        var preview = profile.preview(fixture());

        assertThat(preview.movementCount()).isEqualTo(parsed.movements().size());
        assertThat(preview.observedAccountCount()).isEqualTo(parsed.accounts().size());
        assertThat(preview.sampleRows()).hasSize(parsed.movements().size());
    }

    private byte[] fixture() {
        return """
                <html><title>Razão Analítico</title><body><table>
                <tr><td>Consolidação: Empresa</td><td></td><td></td><td></td><td></td><td></td><td></td><td>Período: 01/01/2026 a 31/01/2026</td></tr>
                <tr><td>Data</td><td>Histórico</td><td></td><td>Lote/Lcto.</td><td></td><td>Débito</td><td>Crédito</td><td>Saldo</td></tr>
                <tr><td>Conta: 111010001</td><td>Red.: 1-9  CAIXA SINTETICO</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>10/01/2026</td><td>MOV A</td><td></td><td>0/10</td><td></td><td>10,00</td><td></td><td>10,00</td></tr>
                <tr><td>Conta: 211010001</td><td>Red.: 2-9  FORNECEDORES SINTETICO</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>Conta: 311010001</td><td>Red.: 3-9  RECEITA SINTETICA</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>11/01/2026</td><td>MOV B</td><td></td><td>0/11</td><td></td><td></td><td>20,00</td><td>-20,00</td></tr>
                </table></body></html>
                """.getBytes(StandardCharsets.ISO_8859_1);
    }
}
