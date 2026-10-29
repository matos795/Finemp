package com.finemp.finance.reconciliation;

import java.math.BigDecimal;
import java.nio.charset.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CsvStatementPreviewParserTest {
    final CsvStatementPreviewParser parser=new CsvStatementPreviewParser();
    @Test void quotedBrazilianValuesAndMultilineMemosRemainExact() {
        var csv="Relatório\nData;Valor;Histórico;Documento\n31/07/2026;\"R$ 1.234,56\";\"Nome, cliente\nSegunda linha\";DOC\n01/08/26;\"(2,00)\";Tarifa;\n";
        var result=parser.parse(csv.getBytes(StandardCharsets.UTF_8),';');assertThat(result.rows()).hasSize(2);var first=result.rows().getFirst().values();
        assertThat(first.postedDate()).isEqualTo(LocalDate.of(2026,7,31));assertThat(first.signedAmount()).isEqualTo(new BigDecimal("1234.56"));assertThat(first.memo()).isEqualTo("Nome, cliente\nSegunda linha");assertThat(first.documentReference()).isEqualTo("DOC");
        assertThat(result.rows().get(1).values().signedAmount()).isEqualTo(new BigDecimal("-2.00"));assertThat(result.toString()).doesNotContain("Nome","DOC");
    }
    @Test void debitCreditAndSeparateDirectionColumnFollowObservedSigns() {
        var csv="Data;Crédito;Débito;Descrição\n2026-07-01;10,00;5,00;Serviço\n2026-07-02;0;3,00;Tarifa\n";
        var rows=parser.parse(csv.getBytes(StandardCharsets.UTF_8),';').rows();assertThat(rows.getFirst().values().signedAmount()).isEqualTo(new BigDecimal("5.00"));assertThat(rows.get(1).values().signedAmount()).isEqualTo(new BigDecimal("-3.00"));
        var directed="Data;Valor;D/C\n2026-07-01;10,00;D\n";assertThat(parser.parse(directed.getBytes(StandardCharsets.UTF_8),';').rows().getFirst().values().signedAmount()).isEqualTo(new BigDecimal("-10.00"));
    }
    @Test void invalidAmountsDatesAndPotentialDuplicatesStayVisible() {
        var csv="Data;Valor;Histórico\n01/07/2026;1,20;TÁXÍ\n01/07/2026;1,20;taxi\n30/02/2026;1,00;Erro\n01/07/2026;1,005;Subcentavo\n01/07/2026;1.234;Ambíguo\n01/07/2026;0;Zero\n01/07/2026;10;Saldo anterior\n";
        var rows=parser.parse(csv.getBytes(StandardCharsets.UTF_8),';').rows();assertThat(rows).hasSize(7);assertThat(rows.getFirst().warnings()).contains(CsvStatementPreviewParser.Warning.DUPLICATE_IN_FILE,CsvStatementPreviewParser.Warning.FALLBACK_IDENTITY);assertThat(rows.get(1).warnings()).contains(CsvStatementPreviewParser.Warning.DUPLICATE_IN_FILE);
        assertThat(rows.get(2).error()).isEqualTo(CsvStatementPreviewParser.Error.BAD_DATE);assertThat(rows.get(3).error()).isEqualTo(CsvStatementPreviewParser.Error.SUBCENT_AMOUNT);assertThat(rows.get(4).error()).isEqualTo(CsvStatementPreviewParser.Error.AMBIGUOUS_AMOUNT);assertThat(rows.get(5).error()).isEqualTo(CsvStatementPreviewParser.Error.ZERO_AMOUNT);assertThat(rows.get(6).warnings()).contains(CsvStatementPreviewParser.Warning.BALANCE_ROW);
    }
    @Test void utf8FallbackWindows1252AndTextFormulasAreNeverExecuted() {
        var csv="Data;Valor;Descrição\n2026-07-01;1,00;Café\n2026-07-02;2,00;=HYPERLINK(\"x\")\n".replace("=HYPERLINK(\"x\")","=SOMA(A1)");
        var rows=parser.parse(csv.getBytes(Charset.forName("windows-1252")),';').rows();assertThat(rows.getFirst().values().memo()).isEqualTo("Café");assertThat(rows.get(1).values().memo()).isEqualTo("=SOMA(A1)");
    }
    @Test void malformedQuotesHeadersAndLimitsFailWithoutPartialSuccess() {
        assertThatThrownBy(()->parser.parse("Data;Valor\n01/07/2026;\"1,00".getBytes(StandardCharsets.UTF_8),';')).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->parser.parse("Nome;Valor\na;1".getBytes(StandardCharsets.UTF_8),';')).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->parser.parse("Data;Valor\n01/07/2026;1".getBytes(StandardCharsets.UTF_8),'#')).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->parser.parse(new byte[16*1024*1024+1],';')).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void mismatchedColumnsAndFormulaAmountsCannotBecomeIncorrectFinancialValues() {
        var csv="Data,Valor,Histórico\n2026-07-01,1,23,Tarifa\n2026-07-02,+SOMA(1+2),Texto\n";
        var rows=parser.parse(csv.getBytes(StandardCharsets.UTF_8),',').rows();assertThat(rows.getFirst().error()).isEqualTo(CsvStatementPreviewParser.Error.BAD_COLUMN_COUNT);assertThat(rows.get(1).error()).isEqualTo(CsvStatementPreviewParser.Error.BAD_AMOUNT);assertThat(rows).allSatisfy(row->assertThat(row.values()).isNull());
    }
    @Test void utf8BomAndQuotedHeaderMustBeAccepted() {
        var csv="\uFEFF\"Data\";Valor\n2026-07-01;1,00\n";assertThat(parser.parse(csv.getBytes(StandardCharsets.UTF_8),';').rows().getFirst().values().signedAmount()).isEqualTo(new BigDecimal("1.00"));
    }
    @Test void arithmeticExpressionsExponentNotationAndMisplacedSeparatorsMustNeverBecomeMoney() {
        var csv="Data;Valor;Histórico\n2026-07-01;-1/2;A\n2026-07-01;-2*3;B\n2026-07-01;1e3;C\n2026-07-01;1,230.00;D\n2026-07-01;12,340.00;E\n";
        var rows=parser.parse(csv.getBytes(StandardCharsets.UTF_8),';').rows();assertThat(rows).hasSize(5);assertThat(rows).allSatisfy(row->{assertThat(row.error()).isEqualTo(CsvStatementPreviewParser.Error.BAD_AMOUNT);assertThat(row.values()).isNull();});
    }
    @Test void validCurrencySignsAndGroupingRemainSupported() {
        var csv="Data;Valor;Histórico\n2026-07-01;-R$ 1.234,56;A\n2026-07-01;D R$ 2,00;B\n";
        var rows=parser.parse(csv.getBytes(StandardCharsets.UTF_8),';').rows();assertThat(rows.getFirst().values().signedAmount()).isEqualTo(new BigDecimal("-1234.56"));assertThat(rows.get(1).values().signedAmount()).isEqualTo(new BigDecimal("-2.00"));
    }
    @Test void conflictingSignIndicatorsAreRejectedAndDebitCreditHeadersRetainDirection() {
        var bad="Data;Valor\n2026-07-01;C 10,00 D\n2026-07-01;(10,00) C\n2026-07-01;-10,00 C\n";
        assertThat(parser.parse(bad.getBytes(StandardCharsets.UTF_8),';').rows()).allSatisfy(row->assertThat(row.error()).isEqualTo(CsvStatementPreviewParser.Error.BAD_AMOUNT));
        var columns="Data;Valor débito;Valor crédito;Histórico\n2026-07-01;100,00;0;Compra\n";
        assertThat(parser.parse(columns.getBytes(StandardCharsets.UTF_8),';').rows().getFirst().values().signedAmount()).isEqualTo(new BigDecimal("-100.00"));
        assertThatThrownBy(()->parser.parse("Data;Valor;Valor débito\n2026-07-01;1;2\n".getBytes(StandardCharsets.UTF_8),';')).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void conflictingDirectionAndTruncatedDatesCannotProduceMovements() {
        var csv="Data;Valor;D/C\n2026-07-01;10,00 C;D\n2026-07-01;-10,00;C\n2026-07-011;10,00;C\n2026-07-01INVALID;10,00;C\n";
        var rows=parser.parse(csv.getBytes(StandardCharsets.UTF_8),';').rows();
        assertThat(rows).extracting(CsvStatementPreviewParser.Row::error).containsExactly(CsvStatementPreviewParser.Error.BAD_AMOUNT,CsvStatementPreviewParser.Error.BAD_AMOUNT,CsvStatementPreviewParser.Error.BAD_DATE,CsvStatementPreviewParser.Error.BAD_DATE);
        assertThat(rows).allSatisfy(row->assertThat(row.values()).isNull());
    }
    @Test void balanceColumnMustNotOverrideMovementAndAmbiguousColumnsMustBeRejected() {
        var csv="Data;Valor saldo;Valor;Histórico\n2026-07-01;1000,00;-20,00;Compra\n";
        assertThat(parser.parse(csv.getBytes(StandardCharsets.UTF_8),';').rows().getFirst().values().signedAmount()).isEqualTo(new BigDecimal("-20.00"));
        assertThatThrownBy(()->parser.parse("Data;Valor movimento;Valor lançado\n2026-07-01;1;2\n".getBytes(StandardCharsets.UTF_8),';')).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->parser.parse("Data;Valor saldo\n2026-07-01;1000\n".getBytes(StandardCharsets.UTF_8),';')).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void salaryBalanceDescriptionMustRemainAValidMovement() {
        var csv="Data;Valor;Histórico\n2026-07-01;10,00;Saldo de salário\n";
        var row=parser.parse(csv.getBytes(StandardCharsets.UTF_8),';').rows().getFirst();
        assertThat(row.values().signedAmount()).isEqualTo(new BigDecimal("10.00"));assertThat(row.error()).isNull();assertThat(row.warnings()).doesNotContain(CsvStatementPreviewParser.Warning.BALANCE_ROW);
    }
    @Test void separateColumnsRejectConflictingSignsAndPreferExactHeader() {
        var csv="Data;Crédito;Débito\n2026-07-01;10,00 D;0\n2026-07-01;0;10,00 C\n2026-07-01;-10,00;0\n2026-07-01;0;+10,00\n";
        assertThat(parser.parse(csv.getBytes(StandardCharsets.UTF_8),';').rows()).allSatisfy(row->{assertThat(row.error()).isEqualTo(CsvStatementPreviewParser.Error.BAD_AMOUNT);assertThat(row.values()).isNull();});
        var exact="Data;Valor crédito previsto;Valor crédito;Valor débito\n2026-07-01;1000,00;10,00;0\n";
        assertThat(parser.parse(exact.getBytes(StandardCharsets.UTF_8),';').rows().getFirst().values().signedAmount()).isEqualTo(new BigDecimal("10.00"));
        assertThatThrownBy(()->parser.parse("Data;Crédito previsto;Crédito futuro;Débito\n2026-07-01;1;2;0\n".getBytes(StandardCharsets.UTF_8),';')).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void dateHeaderPrefersExactAndRejectsMultipleAmbiguousDates() {
        var csv="Data agendamento;Data;Valor;Histórico\n2026-08-01;2026-07-01;-20,00;Compra\n";
        assertThat(parser.parse(csv.getBytes(StandardCharsets.UTF_8),';').rows().getFirst().values().postedDate()).isEqualTo(LocalDate.of(2026,7,1));
        assertThatThrownBy(()->parser.parse("Data agendamento;Data pagamento;Valor\n2026-08-01;2026-07-01;1\n".getBytes(StandardCharsets.UTF_8),';')).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void exactHeadersWinOverMultiplePrefixesAndDuplicateDirectionsFail() {
        var csv="Data agendamento;Data pagamento;Data;Valor previsto;Valor futuro;Valor;Histórico\n2026-08-01;2026-08-02;2026-07-01;100;200;3;Compra\n";
        var row=parser.parse(csv.getBytes(StandardCharsets.UTF_8),';').rows().getFirst();assertThat(row.values().postedDate()).isEqualTo(LocalDate.of(2026,7,1));assertThat(row.values().signedAmount()).isEqualTo(new BigDecimal("3.00"));
        var directed="Data;Crédito previsto;Crédito futuro;Crédito;Débito\n2026-07-01;100;200;3;0\n";
        assertThat(parser.parse(directed.getBytes(StandardCharsets.UTF_8),';').rows().getFirst().values().signedAmount()).isEqualTo(new BigDecimal("3.00"));
        assertThatThrownBy(()->parser.parse("Data;Valor;D/C;C/D\n2026-07-01;10;C;D\n".getBytes(StandardCharsets.UTF_8),';')).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void separateCreditDebitColumnsCannotIgnoreAdditionalDirection() {
        var csv="Data;Crédito;Débito;D/C\n2026-07-01;10;0;D\n2026-07-01;0;10;C\n";
        assertThatThrownBy(()->parser.parse(csv.getBytes(StandardCharsets.UTF_8),';')).isInstanceOf(IllegalArgumentException.class);
    }
}
