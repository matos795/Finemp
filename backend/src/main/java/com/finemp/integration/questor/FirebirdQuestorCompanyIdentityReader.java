package com.finemp.integration.questor;

import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.AccountingSourceType;
import java.sql.*;
import java.util.Objects;

public class FirebirdQuestorCompanyIdentityReader implements AccountingCompanyIdentityReader {
    private final QuestorConnectionProvider provider;
    private final QuestorIdentityMapping mapping;
    private final int timeoutSeconds;
    public FirebirdQuestorCompanyIdentityReader(QuestorConnectionProvider provider, QuestorIdentityMapping mapping, int timeoutSeconds) {
        if (timeoutSeconds < 1 || timeoutSeconds > 120) throw new IllegalArgumentException("Invalid identity query timeout.");
        this.provider = Objects.requireNonNull(provider); this.mapping = Objects.requireNonNull(mapping); this.timeoutSeconds = timeoutSeconds;
    }
    @Override public ExternalCompanyIdentity readIdentity(AccountingReadRequest request) {
        requireCode(request);
        try (var session = QuestorReadOnlySession.open(provider)) { return readOn(session.connection(), request); }
        catch (AccountingIdentityMismatchException mismatch) { throw new AccountingIdentityMismatchException(); }
        catch (AccountingIdentityConfigurationException unsupported) { throw new AccountingIdentityConfigurationException(); }
        catch (SQLException | RuntimeException unavailable) { throw new QuestorReadException("Questor identity read unavailable.", null); }
    }
    ExternalCompanyIdentity readOn(Connection connection, AccountingReadRequest request) throws SQLException {
        int code = requireCode(request); validateMetadata(connection.getMetaData());
        try (var statement = connection.prepareStatement(mapping.sql())) {
            statement.setInt(1, code); statement.setMaxRows(2); statement.setQueryTimeout(timeoutSeconds);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new AccountingIdentityMismatchException();
                int observedCode = result.getInt("FINEMP_OBS_REF"); boolean missingCode = result.wasNull();
                String observedCnpj = result.getString("FINEMP_OBS_CNPJ");
                if (missingCode || observedCode < 1 || observedCnpj == null || observedCnpj.length() > 255 || result.next())
                    throw new AccountingIdentityMismatchException();
                String canonicalCnpj = observedCnpj.replaceAll("[.\\-/\\s]", "");
                if (!canonicalCnpj.matches("[0-9]{14}")) throw new AccountingIdentityMismatchException();
                return new ExternalCompanyIdentity(Integer.toString(observedCode), canonicalCnpj);
            }
        }
    }
    static int requireCode(AccountingReadRequest request) {
        if (request == null || request.sourceType() != AccountingSourceType.QUESTOR) throw new AccountingIdentityConfigurationException();
        try {
            int code = Integer.parseInt(request.externalCompanyReference());
            if (code < 1) throw new AccountingIdentityConfigurationException();
            return code;
        } catch (NumberFormatException invalid) { throw new AccountingIdentityConfigurationException(); }
    }
    private void validateMetadata(DatabaseMetaData metadata) throws SQLException {
        String pattern = literalPattern(mapping.table(), metadata.getSearchStringEscape());
        int matches = 0, scanned = 0;
        try (var tables = metadata.getTables(null, null, pattern, new String[]{"TABLE", "VIEW"})) {
            while (tables.next()) {
                if (++scanned > 4096) throw new AccountingIdentityConfigurationException();
                if (mapping.table().equals(tables.getString("TABLE_NAME"))) matches++;
            }
        }
        if (matches != 1) throw new AccountingIdentityConfigurationException();
        boolean codeFound = false, cnpjFound = false; scanned = 0;
        try (var columns = metadata.getColumns(null, null, pattern, "%")) {
            while (columns.next()) {
                if (++scanned > 4096) throw new AccountingIdentityConfigurationException();
                if (!mapping.table().equals(columns.getString("TABLE_NAME"))) continue;
                String name = columns.getString("COLUMN_NAME"); int type = columns.getInt("DATA_TYPE");
                if (mapping.companyCodeColumn().equals(name)) {
                    int scale = columns.getInt("DECIMAL_DIGITS"); boolean knownScale = !columns.wasNull();
                    boolean integral = type == Types.INTEGER || type == Types.SMALLINT || type == Types.BIGINT
                            || ((type == Types.NUMERIC || type == Types.DECIMAL) && knownScale && scale == 0);
                    if (codeFound || !integral) throw new AccountingIdentityConfigurationException();
                    codeFound = true;
                }
                if (mapping.cnpjColumn().equals(name)) {
                    int length = columns.getInt("COLUMN_SIZE");
                    boolean text = type == Types.CHAR || type == Types.VARCHAR || type == Types.NCHAR || type == Types.NVARCHAR;
                    if (cnpjFound || !text || length < 1 || length > 255) throw new AccountingIdentityConfigurationException();
                    cnpjFound = true;
                }
            }
        }
        if (!codeFound || !cnpjFound) throw new AccountingIdentityConfigurationException();
    }
    private String literalPattern(String value, String escape) {
        if (escape == null || escape.isEmpty()) return value;
        return value.replace(escape, escape + escape).replace("_", escape + "_");
    }
}
