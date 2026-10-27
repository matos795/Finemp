package com.finemp.integration.questor;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.reading.AccountingReadLimitExceededException;

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
                P.CONTACTB AS EXTERNAL_ACCOUNT_ID,
                P.CLASSIFCONTA || '.' || P.CONTACTB AS ACCOUNT_KEY,
                P.DESCRCONTA AS ACCOUNT_DESCRIPTION,
                'CREDIT' AS SIDE,
                L.VALORLCTOCTB AS AMOUNT,
                L.COMPLHIST AS HISTORY
            FROM LCTOCTB L
            JOIN PLANOESPEC P
              ON P.CODIGOEMPRESA = L.CODIGOEMPRESA
             AND P.CONTACTB = L.CONTACTBCRED
            WHERE L.CODIGOEMPRESA = ?
              AND L.DATALCTOCTB BETWEEN ? AND ?

            UNION ALL

            SELECT
                L.CHAVELCTOCTB AS SOURCE_ENTRY_KEY,
                L.DATALCTOCTB AS ACCOUNTING_DATE,
                P.CONTACTB AS EXTERNAL_ACCOUNT_ID,
                P.CLASSIFCONTA || '.' || P.CONTACTB AS ACCOUNT_KEY,
                P.DESCRCONTA AS ACCOUNT_DESCRIPTION,
                'DEBIT' AS SIDE,
                L.VALORLCTOCTB AS AMOUNT,
                L.COMPLHIST AS HISTORY
            FROM LCTOCTB L
            JOIN PLANOESPEC P
              ON P.CODIGOEMPRESA = L.CODIGOEMPRESA
             AND P.CONTACTB = L.CONTACTBDEB
            WHERE L.CODIGOEMPRESA = ?
              AND L.DATALCTOCTB BETWEEN ? AND ?
            """;

    private final QuestorConnectionProvider connectionProvider;
    private final int maxRecords;

    public FirebirdQuestorAccountingReader(
            QuestorConnectionProvider connectionProvider) {
        this(connectionProvider, 25000);
    }

    public FirebirdQuestorAccountingReader(QuestorConnectionProvider connectionProvider, int maxRecords) {
        if (maxRecords < 1 || maxRecords == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid reader resource limit.");
        this.connectionProvider = connectionProvider;
        this.maxRecords = maxRecords;
    }

    @Override
    public List<ImportedAccountingMovement> readMovements(
            String externalCompanyReference,
            LocalDate startDate,
            LocalDate endDate) {
        int companyCode = Integer.parseInt(externalCompanyReference);

        try (
                Connection connection = connectionProvider.openConnection();
                PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setMaxRows(maxRecords + 1);
            setParameters(
                    statement,
                    companyCode,
                    startDate,
                    endDate);

            try (ResultSet resultSet = statement.executeQuery()) {

                return mapMovements(resultSet);
            }

        } catch (SQLException exception) {
            throw new QuestorReadException(
                    "Failed to read accounting movements from Questor",
                    exception);
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

        statement.setInt(4, companyCode);
        statement.setDate(5, Date.valueOf(startDate));
        statement.setDate(6, Date.valueOf(endDate));
    }

    private List<ImportedAccountingMovement> mapMovements(
            ResultSet resultSet) throws SQLException {

        List<ImportedAccountingMovement> movements = new ArrayList<>();

        while (resultSet.next()) {
            if (movements.size() >= maxRecords) throw new AccountingReadLimitExceededException();
            movements.add(
                    new ImportedAccountingMovement(
                            resultSet.getString("SOURCE_ENTRY_KEY"),
                            resultSet.getDate("ACCOUNTING_DATE")
                                    .toLocalDate(),
                            resultSet.getString("EXTERNAL_ACCOUNT_ID"),
                            resultSet.getString("ACCOUNT_KEY"),
                            resultSet.getString("ACCOUNT_DESCRIPTION"),
                            mapSide(resultSet),
                            resultSet.getBigDecimal("AMOUNT"),
                            resultSet.getString("HISTORY")));
        }

        return movements;
    }

    private AccountingMovementSide mapSide(ResultSet resultSet) throws SQLException {
        return AccountingMovementSide.valueOf(
                resultSet.getString("SIDE").trim());
    }
}
