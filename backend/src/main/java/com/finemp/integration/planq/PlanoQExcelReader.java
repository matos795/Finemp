package com.finemp.integration.planq;

import com.finemp.accounting.classification.importing.PlanoQMapping;
import com.finemp.accounting.classification.importing.PlanoQMappingReader;
import com.finemp.accounting.classification.importing.PlanoQReadException;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class PlanoQExcelReader
        implements PlanoQMappingReader {

    private static final String ACCOUNT_KEY = "N_CONTARESUMIDA";

    private static final String DRE = "DRE";

    private static final String DRE_OPEN = "DRE ABERTA";

    private static final String EBITDA = "EBITDA";

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            ACCOUNT_KEY,
            DRE,
            DRE_OPEN,
            EBITDA);

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public List<PlanoQMapping> read(
            InputStream inputStream) {

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {

            HeaderLocation header = findHeader(workbook);

            return readMappings(header);

        } catch (EncryptedDocumentException exception) {

            throw new PlanoQReadException(
                    "Arquivos Excel protegidos por senha não são aceitos.",
                    exception);

        } catch (IOException exception) {

            throw new PlanoQReadException(
                    "Não foi possível ler o arquivo PlanoQ.",
                    exception);
        }
    }

    private HeaderLocation findHeader(
            Workbook workbook) {

        for (Sheet sheet : workbook) {

            for (Row row : sheet) {

                Map<String, Integer> columns = mapHeaderColumns(row);

                if (columns.keySet()
                        .containsAll(REQUIRED_HEADERS)) {

                    return new HeaderLocation(
                            sheet,
                            row.getRowNum(),
                            columns);
                }
            }
        }

        throw new PlanoQReadException(
                "O arquivo não contém as colunas obrigatórias do PlanoQ.");
    }

    private Map<String, Integer> mapHeaderColumns(
            Row row) {

        Map<String, Integer> columns = new HashMap<>();

        for (Cell cell : row) {

            String value = normalizeHeader(
                    dataFormatter
                            .formatCellValue(cell));

            if (REQUIRED_HEADERS.contains(value)) {
                columns.put(
                        value,
                        cell.getColumnIndex());
            }
        }

        return columns;
    }

    private List<PlanoQMapping> readMappings(
            HeaderLocation header) {

        List<PlanoQMapping> mappings = new ArrayList<>();

        Set<String> accountKeys = new HashSet<>();

        Sheet sheet = header.sheet();

        for (int rowIndex = header.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            String accountKey = readCell(
                    row,
                    header.column(ACCOUNT_KEY));

            if (accountKey.isBlank()) {
                continue;
            }

            if (!accountKeys.add(accountKey)) {
                throw new PlanoQReadException(
                        "O arquivo PlanoQ contém uma conta duplicada.");
            }

            mappings.add(
                    new PlanoQMapping(
                            accountKey,
                            readCell(
                                    row,
                                    header.column(DRE)),
                            readCell(
                                    row,
                                    header.column(DRE_OPEN)),
                            readCell(
                                    row,
                                    header.column(EBITDA))));
        }

        return List.copyOf(mappings);
    }

    private String readCell(
            Row row,
            int columnIndex) {

        Cell cell = row.getCell(
                columnIndex,
                Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

        if (cell == null) {
            return "";
        }

        return dataFormatter
                .formatCellValue(cell)
                .trim();
    }

    private String normalizeHeader(
            String value) {

        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private record HeaderLocation(
            Sheet sheet,
            int headerRowIndex,
            Map<String, Integer> columns) {

        int column(String name) {

            Integer column = columns.get(name);

            if (column == null) {
                throw new IllegalStateException(
                        "Coluna obrigatória não encontrada.");
            }

            return column;
        }
    }
}