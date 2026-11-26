package com.finemp.accounting.fileimport;

import com.finemp.accounting.movement.AccountingMovementSide;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public final class AnalyticalLedgerHtmlLayoutProfile implements GeneralLedgerLayoutProfile {
    static final String PROFILE_ID = "analytical-ledger-html-v1";
    private static final int SAMPLE_LIMIT = 20;
    private static final Pattern ROW = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern CELL = Pattern.compile("(?is)<td\\b[^>]*>(.*?)</td>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern PERIOD = Pattern.compile(
            "Período:\\s*(\\d{2}/\\d{2}/\\d{4})\\s*a\\s*(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern ACCOUNT = Pattern.compile("^Conta:\\s*(\\S+)$");
    private static final Pattern REDUCED_ACCOUNT = Pattern.compile("^Red\\.:\\s*\\S+\\s+(.*)$");
    private static final Pattern DATE_SHAPE = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public String id() {
        return PROFILE_ID;
    }

    @Override
    public Set<AccountingFileFormat> formats() {
        return Set.of(AccountingFileFormat.HTML_XLS);
    }

    @Override
    public GeneralLedgerPreview preview(byte[] content) {
        if (content == null || content.length == 0 || content.length > AccountingFileImportLimits.MAX_FILE_BYTES) {
            throw new AccountingFilePreviewException("Arquivo de Razão inválido.");
        }

        String html = new String(content, StandardCharsets.ISO_8859_1);
        if (!html.regionMatches(true, firstNonWhitespace(html), "<html", 0, 5)
                || !html.contains("Razão Analítico")) {
            throw unrecognized();
        }

        List<List<String>> rows = readRows(html);
        if (rows.isEmpty()) {
            throw unrecognized();
        }

        LocalDate periodStart = null;
        LocalDate periodEnd = null;
        boolean expectedHeader = false;
        String currentAccount = null;
        String currentDescription = null;
        Set<String> observedAccounts = new LinkedHashSet<>();
        List<GeneralLedgerPreviewIssue> warnings = new ArrayList<>();
        List<GeneralLedgerPreviewIssue> errors = new ArrayList<>();
        List<GeneralLedgerPreviewRow> samples = new ArrayList<>();
        int movements = 0;
        int invalidRows = 0;

        for (int index = 0; index < rows.size(); index++) {
            int sourceRow = index + 1;
            List<String> row = rows.get(index);
            String joined = String.join(" ", row);

            var periodMatcher = PERIOD.matcher(joined);
            if (periodMatcher.find()) {
                periodStart = parseDate(periodMatcher.group(1), sourceRow, "INVALID_PERIOD", errors);
                periodEnd = parseDate(periodMatcher.group(2), sourceRow, "INVALID_PERIOD", errors);
                continue;
            }

            if (isExpectedHeader(row)) {
                expectedHeader = true;
                continue;
            }

            var accountMatcher = ACCOUNT.matcher(cell(row, 0));
            if (accountMatcher.matches()) {
                currentAccount = accountMatcher.group(1);
                currentDescription = accountDescription(cell(row, 1));
                observedAccounts.add(currentAccount);
                continue;
            }

            String rawDate = cell(row, 0);
            if (!DATE_SHAPE.matcher(rawDate).matches()) {
                continue;
            }

            LocalDate accountingDate = parseDate(rawDate, sourceRow, "INVALID_DATE", errors);
            String sourceEntryKey = cell(row, 3);
            String debit = cell(row, 5);
            String credit = cell(row, 6);

            if (currentAccount == null || currentAccount.isBlank()) {
                invalidRows++;
                errors.add(issue("MOVEMENT_WITHOUT_ACCOUNT", sourceRow,
                        "A linha de movimento não possui uma conta de contexto."));
                continue;
            }
            if (accountingDate == null) {
                invalidRows++;
                continue;
            }
            if (periodStart != null && periodEnd != null
                    && (accountingDate.isBefore(periodStart) || accountingDate.isAfter(periodEnd))) {
                invalidRows++;
                errors.add(issue("MOVEMENT_OUTSIDE_PERIOD", sourceRow,
                        "A data do movimento está fora do período declarado no relatório."));
                continue;
            }
            if (sourceEntryKey.isBlank()) {
                invalidRows++;
                errors.add(issue("MOVEMENT_KEY_MISSING", sourceRow,
                        "A linha de movimento não possui Lote/Lcto."));
                continue;
            }

            boolean hasDebit = !debit.isBlank();
            boolean hasCredit = !credit.isBlank();
            if (hasDebit == hasCredit) {
                invalidRows++;
                errors.add(issue("MOVEMENT_SIDE_AMBIGUOUS", sourceRow,
                        "A linha possui débito e crédito ao mesmo tempo."));
                continue;
            }

            BigDecimal amount;
            BigDecimal balance;
            try {
                amount = parseBrazilianMoney(hasDebit ? debit : credit);
                balance = cell(row, 7).isBlank() ? null : parseBrazilianMoney(cell(row, 7));
            } catch (NumberFormatException invalidMoney) {
                invalidRows++;
                errors.add(issue("INVALID_MONEY", sourceRow,
                        "A linha possui valor monetário inválido."));
                continue;
            }

            movements++;
            if (samples.size() < SAMPLE_LIMIT) {
                samples.add(new GeneralLedgerPreviewRow(
                        sourceRow,
                        currentAccount,
                        currentDescription,
                        accountingDate,
                        sourceEntryKey,
                        hasDebit ? AccountingMovementSide.DEBIT : AccountingMovementSide.CREDIT,
                        amount,
                        cell(row, 1),
                        balance));
            }
        }

        if (!expectedHeader || periodStart == null || periodEnd == null || observedAccounts.isEmpty()) {
            throw unrecognized();
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new AccountingFilePreviewException("Período do Razão inválido.");
        }

        return new GeneralLedgerPreview(
                id(),
                AccountingFileFormat.HTML_XLS,
                periodStart,
                periodEnd,
                rows.size(),
                movements,
                invalidRows,
                observedAccounts.size(),
                warnings,
                errors,
                samples);
    }

    private static List<List<String>> readRows(String html) {
        List<List<String>> rows = new ArrayList<>();
        var rowMatcher = ROW.matcher(html);
        while (rowMatcher.find()) {
            List<String> cells = new ArrayList<>();
            var cellMatcher = CELL.matcher(rowMatcher.group(1));
            while (cellMatcher.find()) {
                cells.add(normalizeCell(cellMatcher.group(1)));
            }
            rows.add(List.copyOf(cells));
        }
        return rows;
    }

    private static boolean isExpectedHeader(List<String> row) {
        return "Data".equals(cell(row, 0))
                && "Histórico".equals(cell(row, 1))
                && "Lote/Lcto.".equals(cell(row, 3))
                && "Débito".equals(cell(row, 5))
                && "Crédito".equals(cell(row, 6))
                && "Saldo".equals(cell(row, 7));
    }

    private static String accountDescription(String raw) {
        var matcher = REDUCED_ACCOUNT.matcher(raw);
        return matcher.matches() ? matcher.group(1).trim() : raw.trim();
    }

    private static LocalDate parseDate(
            String raw,
            int sourceRow,
            String errorCode,
            List<GeneralLedgerPreviewIssue> errors) {
        try {
            return LocalDate.parse(raw, DATE);
        } catch (DateTimeParseException invalid) {
            errors.add(issue(errorCode, sourceRow, "A linha possui data inválida."));
            return null;
        }
    }

    private static BigDecimal parseBrazilianMoney(String raw) {
        String normalized = raw.trim().replace(".", "").replace(",", ".");
        if (!normalized.matches("-?\\d+(?:\\.\\d{1,4})?")) {
            throw new NumberFormatException();
        }
        return new BigDecimal(normalized);
    }

    private static String normalizeCell(String rawHtml) {
        String withoutTags = TAG.matcher(rawHtml).replaceAll("");
        return HtmlUtils.htmlUnescape(withoutTags)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }

    private static int firstNonWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return 0;
    }

    private static GeneralLedgerPreviewIssue issue(String code, int row, String message) {
        return new GeneralLedgerPreviewIssue(code, row, message);
    }

    private static AccountingFilePreviewException unrecognized() {
        return new AccountingFilePreviewException("Formato de Razão não reconhecido.");
    }
}
