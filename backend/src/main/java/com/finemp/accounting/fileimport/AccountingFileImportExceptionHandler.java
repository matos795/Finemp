package com.finemp.accounting.fileimport;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AccountingFileImportController.class)
public final class AccountingFileImportExceptionHandler {

    @ExceptionHandler(AccountingFilePreviewException.class)
    ResponseEntity<ApiError> preview(AccountingFilePreviewException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_GENERAL_LEDGER", exception.getMessage()));
    }

    @ExceptionHandler(AccountingFileLayoutUnavailableException.class)
    ResponseEntity<ApiError> layout() {
        return ResponseEntity.badRequest().body(new ApiError(
                "LAYOUT_UNAVAILABLE",
                "O layout de Razão informado não está disponível para este formato."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalidFile() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(
                "INVALID_FILE",
                "O arquivo enviado é vazio, inválido ou excede o limite permitido."));
    }

    record ApiError(String code, String message) {
    }
}
