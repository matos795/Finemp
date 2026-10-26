package com.finemp.integration.planq;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.finemp.accounting.classification.importing.PlanoQReadException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PlanoQUploadValidatorTest {

    @Test
    void shouldRejectUnsupportedExtension() {

        var file = new MockMultipartFile(
                "file",
                "planoq.pdf",
                "application/pdf",
                "%PDF".getBytes());

        var validator = new PlanoQUploadValidator();

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(PlanoQReadException.class)
                .hasMessageContaining(
                        ".xlsx ou .xlsm");
    }

    @Test
    void shouldRejectFakeExcelFile() {

        var file = new MockMultipartFile(
                "file",
                "planoq.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "isso não é um excel".getBytes());

        var validator = new PlanoQUploadValidator();

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(PlanoQReadException.class)
                .hasMessageContaining(
                        "não é um arquivo Excel válido");
    }
}
