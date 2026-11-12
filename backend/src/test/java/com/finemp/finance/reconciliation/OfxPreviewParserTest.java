package com.finemp.finance.reconciliation;

import java.math.BigDecimal;
import java.nio.charset.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OfxPreviewParserTest {
    final OfxPreviewParser parser=new OfxPreviewParser();
    @Test void sgmlAndXmlTransactionsMustPreserveSignedExactMoneyDatesAndDocuments() {
        var file="OFXHEADER:100\n<OFX><BANKID>001\n<ACCTID>123\n<BANKTRANLIST>"+
                "<STMTTRN><TRNAMT>-1.20\n<DTPOSTED>20260701123000[-3:BRT]\n<NAME>Serviço\n<MEMO>Tarifa\n<FITID>ID1\n<CHECKNUM>DOC\n</STMTTRN>"+
                "<STMTTRN><TRNAMT>2,00</TRNAMT><DTPOSTED>20260702</DTPOSTED><TRNTYPE>CREDIT</TRNTYPE><REFNUM>REF</REFNUM></STMTTRN></BANKTRANLIST>"+
                "<LEDGERBAL><BALAMT>8.23\n<DTASOF>20260731\n</LEDGERBAL></OFX>";
        var result=parser.parse(file.getBytes(StandardCharsets.UTF_8));assertThat(result.rows()).hasSize(2);assertThat(result.bankCode()).isEqualTo("001");assertThat(result.accountReference()).isEqualTo("123");
        var first=result.rows().getFirst().values();assertThat(first.signedAmount()).isEqualTo(new BigDecimal("-1.20"));assertThat(first.postedDate()).isEqualTo(LocalDate.of(2026,7,1));assertThat(first.memo()).isEqualTo("Serviço – Tarifa");assertThat(first.externalId()).isEqualTo("ID1");assertThat(first.documentReference()).isEqualTo("DOC");
        assertThat(result.rows().get(1).values().documentReference()).isEqualTo("REF");assertThat(result.rows().get(1).warnings()).contains(OfxPreviewParser.Warning.FALLBACK_IDENTITY);
        assertThat(result.balance().amount()).isEqualTo(new BigDecimal("8.23"));assertThat(result.balance().date()).isEqualTo(LocalDate.of(2026,7,31));assertThat(result.toString()).doesNotContain("Serviço","123");
        assertThat(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(result.balance())).contains("\"amount\":\"8.23\"");
    }
    @Test void invalidLinesAndSubcentValuesMustRemainVisibleWithoutRounding() {
        var file="<OFX><BANKTRANLIST><STMTTRN><TRNAMT>1.005\n<DTPOSTED>20260701\n</STMTTRN><STMTTRN><TRNAMT>0\n<DTPOSTED>20260701\n</STMTTRN><STMTTRN><TRNAMT>1\n<DTPOSTED>20260230\n</STMTTRN></BANKTRANLIST></OFX>";
        var rows=parser.parse(file.getBytes(StandardCharsets.UTF_8)).rows();assertThat(rows).hasSize(3);assertThat(rows).extracting(OfxPreviewParser.Row::error).containsExactly(OfxPreviewParser.Error.SUBCENT_AMOUNT,OfxPreviewParser.Error.ZERO_AMOUNT,OfxPreviewParser.Error.BAD_DATE);assertThat(rows).allSatisfy(row->assertThat(row.values()).isNull());
    }
    @Test void duplicatedFallbackAndFitIdsMustBeWarningsOnAllAffectedRows() {
        var file="<OFX><BANKTRANLIST><STMTTRN><TRNAMT>1\n<DTPOSTED>20260701\n<MEMO>TÁXÍ\n</STMTTRN><STMTTRN><TRNAMT>1.00\n<DTPOSTED>20260701\n<MEMO>taxi\n</STMTTRN><STMTTRN><TRNAMT>2\n<DTPOSTED>20260701\n<FITID>same\n</STMTTRN><STMTTRN><TRNAMT>9\n<DTPOSTED>20260702\n<FITID>same\n</STMTTRN></BANKTRANLIST></OFX>";
        var rows=parser.parse(file.getBytes(StandardCharsets.UTF_8)).rows();assertThat(rows).hasSize(4);assertThat(rows).allSatisfy(row->assertThat(row.warnings()).contains(OfxPreviewParser.Warning.DUPLICATE_IN_FILE));assertThat(rows.getFirst().warnings()).contains(OfxPreviewParser.Warning.FALLBACK_IDENTITY);
    }
    @Test void windows1252AndUtf8MustDecodeWithoutExternalResolution() {
        var file="CHARSET:1252\n<OFX><BANKTRANLIST><STMTTRN><TRNAMT>1\n<DTPOSTED>20260701\n<MEMO>Café\n</STMTTRN></BANKTRANLIST></OFX>";
        var result=parser.parse(file.getBytes(Charset.forName("windows-1252")));assertThat(result.rows().getFirst().values().memo()).isEqualTo("Café");
        var unsafe=file.replace("<OFX>","<!DOCTYPE OFX [<!ENTITY ext SYSTEM 'file:///not-read'>]><OFX>");assertThatThrownBy(()->parser.parse(unsafe.getBytes(Charset.forName("windows-1252")))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void malformedOrOversizedFilesMustNotReturnAnIncompleteSuccess() {
        assertThatThrownBy(()->parser.parse(new byte[0])).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->parser.parse(new byte[16*1024*1024+1])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->parser.parse("<OFX>none</OFX>".getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        var rows=parser.parse("<OFX><STMTTRN><TRNAMT>1".getBytes(StandardCharsets.UTF_8)).rows();assertThat(rows).hasSize(1);assertThat(rows.getFirst().error()).isEqualTo(OfxPreviewParser.Error.UNTERMINATED);
    }
    @Test void multipleAccountsOrBanksMustBeFlaggedBeforeAnyFutureConfirmation() {
        var file="<OFX><BANKID>001\n<ACCTID>123\n<BANKTRANLIST><STMTTRN><TRNAMT>1\n<DTPOSTED>20260701\n</STMTTRN></BANKTRANLIST><BANKID>002\n<ACCTID>456\n<BANKTRANLIST><STMTTRN><TRNAMT>2\n<DTPOSTED>20260701\n</STMTTRN></BANKTRANLIST></OFX>";
        var result=parser.parse(file.getBytes(StandardCharsets.UTF_8));assertThat(result.rows()).hasSize(2);assertThat(result.warnings()).contains(OfxPreviewParser.Warning.MULTIPLE_BANKS,OfxPreviewParser.Warning.MULTIPLE_ACCOUNTS);
    }
    @Test void missingForeignOrConflictingCurrencyMustRemainVisibleForBusinessValidation() {
        var prefix="<OFX><CURDEF>USD\n<CURDEF>BRL\n<BANKTRANLIST><STMTTRN><TRNAMT>1\n<DTPOSTED>20260701\n</STMTTRN></BANKTRANLIST></OFX>";
        var result=parser.parse(prefix.getBytes(StandardCharsets.UTF_8));assertThat(result.currency()).isEqualTo("USD");assertThat(result.warnings()).contains(OfxPreviewParser.Warning.CURRENCY_UNCONFIRMED,OfxPreviewParser.Warning.MULTIPLE_CURRENCIES);
    }
    @Test void xmlEscapesMustDecodeWithoutResolvingCustomEntities() {
        var xml="<?xml version=\"1.0\" encoding=\"UTF-8\"?><OFX><BANKTRANLIST><STMTTRN><TRNAMT>1.00</TRNAMT><DTPOSTED>20260701</DTPOSTED><MEMO>A &amp; B &#x1F600;</MEMO><FITID>id&amp;1</FITID><CHECKNUM>&lt;X&gt;</CHECKNUM></STMTTRN></BANKTRANLIST></OFX>";
        var row=parser.parse(xml.getBytes(StandardCharsets.UTF_8)).rows().getFirst();assertThat(row.values().memo()).isEqualTo("A & B 😀");assertThat(row.values().externalId()).isEqualTo("id&1");assertThat(row.values().documentReference()).isEqualTo("<X>");
        assertThat(row.values().identityKey()).isEqualTo(new StatementValues(LocalDate.of(2026,7,1),BigDecimal.ONE,"A & B 😀","id&1","").identityKey());
        var dangerous=xml.replace("A &amp; B &#x1F600;","&external;");assertThat(parser.parse(dangerous.getBytes(StandardCharsets.UTF_8)).rows().getFirst().error()).isEqualTo(OfxPreviewParser.Error.INVALID_TEXT);
    }
    @Test void charsetDeclarationsMustTakePrecedenceOverWordsInTheTransaction() {
        var xml="<?xml version=\"1.0\" encoding=\"UTF-8\"?><OFX><BANKTRANLIST><STMTTRN><TRNAMT>1</TRNAMT><DTPOSTED>20260701</DTPOSTED><MEMO>Café WINDOWS-1252</MEMO></STMTTRN></BANKTRANLIST></OFX>";
        assertThat(parser.parse(xml.getBytes(StandardCharsets.UTF_8)).rows().getFirst().values().memo()).isEqualTo("Café WINDOWS-1252");
        var sgml="OFXHEADER:100\n<OFX><BANKTRANLIST><STMTTRN><TRNAMT>1\n<DTPOSTED>20260701\n<MEMO>Café WINDOWS-1252\n</BANKTRANLIST></OFX>";
        assertThat(parser.parse(sgml.getBytes(StandardCharsets.UTF_8)).rows().getFirst().values().memo()).isEqualTo("Café WINDOWS-1252");
    }
    @Test void xmlRequiresClosedTransactionsWhileSgmlPermitsTheLegacyOpenForm() {
        var xml="<?xml version=\"1.0\"?><OFX><BANKTRANLIST><STMTTRN><TRNAMT>1</TRNAMT><DTPOSTED>20260701</DTPOSTED><STMTTRN><TRNAMT>2</TRNAMT><DTPOSTED>20260701</DTPOSTED></STMTTRN></BANKTRANLIST></OFX>";
        var rows=parser.parse(xml.getBytes(StandardCharsets.UTF_8)).rows();assertThat(rows.getFirst().error()).isEqualTo(OfxPreviewParser.Error.UNTERMINATED);assertThat(rows.getFirst().values()).isNull();assertThat(rows.get(1).values()).isNotNull();
        var sgml="OFXHEADER:100\n<OFX><BANKTRANLIST><STMTTRN><TRNAMT>1\n<DTPOSTED>20260701\n<STMTTRN><TRNAMT>2\n<DTPOSTED>20260701\n</BANKTRANLIST></OFX>";assertThat(parser.parse(sgml.getBytes(StandardCharsets.UTF_8)).rows()).allSatisfy(row->assertThat(row.values()).isNotNull());
    }
    @Test void duplicateFitIdMustBeFlaggedEvenWhenOneRowHasInvalidDate() {
        var sgml="OFXHEADER:100\n<OFX><BANKTRANLIST><STMTTRN><TRNAMT>1\n<DTPOSTED>20260701\n<FITID>same\n<STMTTRN><TRNAMT>1\n<DTPOSTED>20260230\n<FITID>same\n</BANKTRANLIST></OFX>";
        var rows=parser.parse(sgml.getBytes(StandardCharsets.UTF_8)).rows();assertThat(rows.get(1).error()).isEqualTo(OfxPreviewParser.Error.BAD_DATE);assertThat(rows).allSatisfy(row->assertThat(row.warnings()).contains(OfxPreviewParser.Warning.DUPLICATE_IN_FILE));
    }
    @Test void xmlMultilineFieldsMustNotBeTruncatedOrMistakenForEmpty() {
        var xml="<?xml version=\"1.0\"?><OFX><BANKTRANLIST><STMTTRN><TRNAMT>\n1.20\n</TRNAMT><DTPOSTED>\n20260701\n</DTPOSTED><MEMO>Compra\nparcela 2</MEMO></STMTTRN></BANKTRANLIST></OFX>";
        var row=parser.parse(xml.getBytes(StandardCharsets.UTF_8)).rows().getFirst();assertThat(row.error()).isNull();assertThat(row.values().memo()).isEqualTo("Compra\nparcela 2");assertThat(row.values().signedAmount()).isEqualTo(new BigDecimal("1.20"));assertThat(row.values().postedDate()).isEqualTo(LocalDate.of(2026,7,1));
    }
    @Test void xmlCommentAndUnsupportedMarkupMustNotCreateOrSilentlyModifyTransactions() {
        var xml="<?xml version=\"1.0\"?><OFX><!-- <STMTTRN><TRNAMT>9</TRNAMT><DTPOSTED>20260701</DTPOSTED></STMTTRN> --><BANKTRANLIST><STMTTRN><TRNAMT>1</TRNAMT><DTPOSTED>20260701</DTPOSTED><MEMO><![CDATA[texto]]></MEMO></STMTTRN></BANKTRANLIST></OFX>";
        assertThatThrownBy(()->parser.parse(xml.getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        var forged=xml.replace("<![CDATA[texto]]>","<![CDATA[<STMTTRN><DTPOSTED>20260702</DTPOSTED><TRNAMT>999</TRNAMT></STMTTRN>]]>");
        assertThatThrownBy(()->parser.parse(forged.getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        var unsupported=xml.replace("<MEMO><![CDATA[texto]]></MEMO>","<MEMO xml:space=\"preserve\">ignored</MEMO><MEMO>valid</MEMO>");assertThat(parser.parse(unsupported.getBytes(StandardCharsets.UTF_8)).rows().getFirst().error()).isEqualTo(OfxPreviewParser.Error.INVALID_TEXT);
        assertThatThrownBy(()->parser.parse(xml.replace("<OFX>","<!DOCTYPE OFX [<!ENTITY x SYSTEM 'file:///not-read'>]><OFX>").getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void misleadingLegacyCharsetMustNotMojibakeValidUtf8Bytes() {
        var file="OFXHEADER:100\nCHARSET:1252\n<OFX><BANKTRANLIST><STMTTRN><TRNAMT>-10.00\n<DTPOSTED>20261112\n<MEMO>Serviço em São Paulo - Café\n</STMTTRN></BANKTRANLIST></OFX>";
        var row=parser.parse(file.getBytes(StandardCharsets.UTF_8)).rows().getFirst();
        assertThat(row.values().memo()).isEqualTo("Serviço em São Paulo - Café");
    }

}
