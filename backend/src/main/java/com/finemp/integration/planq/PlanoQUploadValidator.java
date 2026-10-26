package com.finemp.integration.planq;

import com.finemp.accounting.classification.importing.PlanoQReadException;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

@Component
public class PlanoQUploadValidator {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".xlsx",
            ".xlsm");

    public void validate(
            MultipartFile file) {

        if (file.isEmpty()) {
            throw new PlanoQReadException(
                    "O arquivo PlanoQ está vazio.");
        }

        validateSize(file);
        validateExtension(file);
        validateFileSignature(file);
    }

    private void validateSize(
            MultipartFile file) {

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new PlanoQReadException(
                    "O arquivo PlanoQ excede o limite de 20 MB.");
        }
    }

    private void validateExtension(
            MultipartFile file) {

        String filename = file.getOriginalFilename();

        if (filename == null) {
            throw new PlanoQReadException(
                    "O arquivo enviado não possui nome.");
        }

        String normalized = filename.toLowerCase(Locale.ROOT);

        boolean allowed = ALLOWED_EXTENSIONS.stream()
                .anyMatch(
                        normalized::endsWith);

        if (!allowed) {
            throw new PlanoQReadException(
                    "Somente arquivos .xlsx ou .xlsm são aceitos.");
        }
    }

    private void validateFileSignature(
            MultipartFile file) {

        try (InputStream inputStream = FileMagic.prepareToCheckMagic(
                file.getInputStream())) {

            FileMagic magic = FileMagic.valueOf(
                    inputStream);

            if (magic != FileMagic.OOXML) {
                throw new PlanoQReadException(
                        "O conteúdo enviado não é um arquivo Excel válido.");
            }

        } catch (IOException exception) {
            throw new PlanoQReadException(
                    "Não foi possível validar o arquivo enviado.",
                    exception);
        }
    }
}