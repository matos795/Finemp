package com.finemp.integration.planq;

import com.finemp.accounting.classification.importing.PlanoQReadException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanoQExcelReaderTest {

    private final PlanoQExcelReader reader = new PlanoQExcelReader();

    @Test
    void shouldReadPlanoQMappingByHeaderName() throws Exception {

        byte[] file = createWorkbookWithValidMapping();

        var mappings = reader.read(
                new ByteArrayInputStream(file));

        assertThat(mappings)
                .hasSize(1);

        var mapping = mappings.getFirst();

        assertThat(mapping.accountKey())
                .isEqualTo(
                        "3.1.01.005.001.009.5396");

        assertThat(mapping.dreClassification())
                .isEqualTo("EBITDA");

        assertThat(mapping.dreOpenClassification())
                .isEqualTo("RECEITA BRUTA");

        assertThat(mapping.ebitdaClassification())
                .isEqualTo(
                        "LOCAÇÃO DE BENS MÓVEIS");
    }

    @Test
    void shouldRejectFileWithoutRequiredHeaders()
            throws Exception {

        byte[] file;

        try (
                var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {

            var sheet = workbook.createSheet("Qualquer aba");

            var header = sheet.createRow(0);

            header.createCell(0)
                    .setCellValue("Descrição");

            header.createCell(1)
                    .setCellValue("Valor");

            workbook.write(output);

            file = output.toByteArray();
        }

        assertThatThrownBy(() -> reader.read(
                new ByteArrayInputStream(file)))
                .isInstanceOf(
                        PlanoQReadException.class)
                .hasMessageContaining(
                        "colunas obrigatórias");
    }

    @Test
    void shouldRejectDuplicatedAccountKey()
            throws Exception {

        byte[] file;

        try (
                var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {

            var sheet = workbook.createSheet("PlanoQ");

            createHeaders(sheet.createRow(0));

            createMappingRow(
                    sheet.createRow(1),
                    "3.1.01.100");

            createMappingRow(
                    sheet.createRow(2),
                    "3.1.01.100");

            workbook.write(output);

            file = output.toByteArray();
        }

        assertThatThrownBy(() -> reader.read(
                new ByteArrayInputStream(file)))
                .isInstanceOf(
                        PlanoQReadException.class)
                .hasMessageContaining("duplicada");
    }

    private byte[] createWorkbookWithValidMapping()
            throws Exception {

        try (
                var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {

            var sheet = workbook.createSheet(
                    "Nome irrelevante");

            var header = sheet.createRow(2);

            header.createCell(0)
                    .setCellValue("Descrição");

            header.createCell(1)
                    .setCellValue("EBITDA");

            header.createCell(2)
                    .setCellValue("N_CONTARESUMIDA");

            header.createCell(3)
                    .setCellValue("DRE ABERTA");

            header.createCell(4)
                    .setCellValue("DRE");

            var row = sheet.createRow(3);

            row.createCell(0)
                    .setCellValue(
                            "Receita de Locação");

            row.createCell(1)
                    .setCellValue(
                            "LOCAÇÃO DE BENS MÓVEIS");

            row.createCell(2)
                    .setCellValue(
                            "3.1.01.005.001.009.5396");

            row.createCell(3)
                    .setCellValue(
                            "RECEITA BRUTA");

            row.createCell(4)
                    .setCellValue(
                            "EBITDA");

            workbook.write(output);

            return output.toByteArray();
        }
    }

    private void createHeaders(
            org.apache.poi.ss.usermodel.Row row) {

        row.createCell(0)
                .setCellValue("N_CONTARESUMIDA");

        row.createCell(1)
                .setCellValue("DRE");

        row.createCell(2)
                .setCellValue("DRE ABERTA");

        row.createCell(3)
                .setCellValue("EBITDA");
    }

    private void createMappingRow(
            org.apache.poi.ss.usermodel.Row row,
            String accountKey) {

        row.createCell(0)
                .setCellValue(accountKey);

        row.createCell(1)
                .setCellValue("EBITDA");

        row.createCell(2)
                .setCellValue("RECEITA BRUTA");

        row.createCell(3)
                .setCellValue("SERVIÇOS");
    }
}