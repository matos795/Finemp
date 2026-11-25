package com.finemp.integration.questor;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.reading.AccountingReadLimitExceededException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class FirebirdQuestorAccountingReader
        implements QuestorAccountingReader {

    private static final String SQL = """
            SELECT
                L.CHAVELCTOCTB AS SOURCE_ENTRY_KEY,
                L.DATALCTOCTB AS ACCOUNTING_DATE,
                L.CONTACTBCRED AS CREDIT_SOURCE_ACCOUNT_ID,
                PC.CONTACTB AS CREDIT_EXTERNAL_ACCOUNT_ID,
                PC.CLASSIFCONTA || '.' || PC.CONTACTB AS CREDIT_ACCOUNT_KEY,
                PC.DESCRCONTA AS CREDIT_ACCOUNT_DESCRIPTION,
                L.CONTACTBDEB AS DEBIT_SOURCE_ACCOUNT_ID,
                PD.CONTACTB AS DEBIT_EXTERNAL_ACCOUNT_ID,
                PD.CLASSIFCONTA || '.' || PD.CONTACTB AS DEBIT_ACCOUNT_KEY,
                PD.DESCRCONTA AS DEBIT_ACCOUNT_DESCRIPTION,
                L.VALORLCTOCTB AS AMOUNT,
                L.COMPLHIST AS HISTORY
            FROM LCTOCTB L
            LEFT JOIN PLANOESPEC PC
              ON PC.CODIGOEMPRESA = L.CODIGOEMPRESA
             AND PC.CONTACTB = L.CONTACTBCRED
            LEFT JOIN PLANOESPEC PD
              ON PD.CODIGOEMPRESA = L.CODIGOEMPRESA
             AND PD.CONTACTB = L.CONTACTBDEB
            WHERE L.CODIGOEMPRESA = ?
              AND L.DATALCTOCTB BETWEEN ? AND ?
            """;

    private final QuestorConnectionProvider connectionProvider;
    private final int maxRecords;
    private final int timeoutSeconds;

    public FirebirdQuestorAccountingReader(
            QuestorConnectionProvider connectionProvider) {
        this(connectionProvider, 25000);
    }

    public FirebirdQuestorAccountingReader(QuestorConnectionProvider connectionProvider, int maxRecords) {
        this(connectionProvider, maxRecords, 30);
    }

    public FirebirdQuestorAccountingReader(
            QuestorConnectionProvider connectionProvider,
            int maxRecords,
            int timeoutSeconds) {
        if (maxRecords < 1 || maxRecords == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid reader resource limit.");
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 120) {
            throw new IllegalArgumentException("Invalid Questor query timeout.");
        }
        this.connectionProvider = connectionProvider;
        this.maxRecords = maxRecords;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public List<ImportedAccountingMovement> readMovements(
            String externalCompanyReference,
            LocalDate startDate,
            LocalDate endDate) {
        Integer.parseInt(externalCompanyReference); // Validate before opening the source session.

        try (var session = QuestorReadOnlySession.open(connectionProvider)) {
            return readOn(session.connection(), externalCompanyReference, startDate, endDate);
        } catch (AccountingReadLimitExceededException overflow) {
            throw new AccountingReadLimitExceededException();
        } catch (QuestorUnexpectedSourceDataException unexpected) {
            throw new QuestorUnexpectedSourceDataException();
        } catch (SQLException | RuntimeException exception) {
            throw new QuestorReadException("Failed to read accounting movements from Questor", null);
        }
    }

    List<ImportedAccountingMovement> readOn(
            Connection connection,
            String externalCompanyReference,
            LocalDate startDate,
            LocalDate endDate) throws SQLException {
        int companyCode = Integer.parseInt(externalCompanyReference);

        try (PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setMaxRows(maxRecords + 1);
            statement.setQueryTimeout(timeoutSeconds);
            setParameters(statement, companyCode, startDate, endDate);

            try (ResultSet resultSet = statement.executeQuery()) {
                return mapMovements(resultSet);
            }
        }
    }

    private void setParameters(
            PreparedStatement statement,
            int companyCode,
            LocalDate startDate,
            LocalDate endDate) throws SQLException {
        statement.setInt(1, companyCode);
        statement.setDate(2, Date.valueOf(startDate));
        statement.setDate(3, Date.valueOf(endDate));
    }

    private List<ImportedAccountingMovement> mapMovements(
            ResultSet resultSet) throws SQLException {
        List<ImportedAccountingMovement> movements = new ArrayList<>();

        while (resultSet.next()) {
            String creditSourceAccountId = normalized(resultSet.getString("CREDIT_SOURCE_ACCOUNT_ID"));
            String debitSourceAccountId = normalized(resultSet.getString("DEBIT_SOURCE_ACCOUNT_ID"));
            BigDecimal amount = resultSet.getBigDecimal("AMOUNT");

            validateSourceRow(
                    creditSourceAccountId,
                    debitSourceAccountId,
                    normalized(resultSet.getString("CREDIT_EXTERNAL_ACCOUNT_ID")),
                    normalized(resultSet.getString("DEBIT_EXTERNAL_ACCOUNT_ID")),
                    amount);

            if (creditSourceAccountId != null) {
                addMovement(
                        movements,
                        resultSet,
                        AccountingMovementSide.CREDIT,
                        "CREDIT_EXTERNAL_ACCOUNT_ID",
                        "CREDIT_ACCOUNT_KEY",
                        "CREDIT_ACCOUNT_DESCRIPTION",
                        amount);
            }

            if (debitSourceAccountId != null) {
                addMovement(
                        movements,
                        resultSet,
                        AccountingMovementSide.DEBIT,
                        "DEBIT_EXTERNAL_ACCOUNT_ID",
                        "DEBIT_ACCOUNT_KEY",
                        "DEBIT_ACCOUNT_DESCRIPTION",
                        amount);
            }
        }

        return movements;
    }

    private void validateSourceRow(
            String creditSourceAccountId,
            String debitSourceAccountId,
            String creditPlanAccountId,
            String debitPlanAccountId,
            BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new QuestorUnexpectedSourceDataException();
        }
        if (creditSourceAccountId == null && debitSourceAccountId == null) {
            throw new QuestorUnexpectedSourceDataException();
        }
        if (creditSourceAccountId != null && creditSourceAccountId.equals(debitSourceAccountId)) {
            throw new QuestorUnexpectedSourceDataException();
        }
        if (creditSourceAccountId != null && creditPlanAccountId == null) {
            throw new QuestorUnexpectedSourceDataException();
        }
        if (debitSourceAccountId != null && debitPlanAccountId == null) {
            throw new QuestorUnexpectedSourceDataException();
        }
    }

    private void addMovement(
            List<ImportedAccountingMovement> movements,
            ResultSet resultSet,
            AccountingMovementSide side,
            String externalAccountColumn,
            String accountKeyColumn,
            String accountDescriptionColumn,
            BigDecimal amount) throws SQLException {
        if (movements.size() >= maxRecords) {
            throw new AccountingReadLimitExceededException();
        }

        movements.add(
                new ImportedAccountingMovement(
                        resultSet.getString("SOURCE_ENTRY_KEY"),
                        resultSet.getDate("ACCOUNTING_DATE").toLocalDate(),
                        resultSet.getString(externalAccountColumn),
                        resultSet.getString(accountKeyColumn),
                        resultSet.getString(accountDescriptionColumn),
                        side,
                        amount,
                        resultSet.getString("HISTORY")));
    }

    private String normalized(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
