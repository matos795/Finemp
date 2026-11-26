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

    @ExceptionHandler(AccountingFileImportConflictException.class)
    ResponseEntity<ApiError> conflict(AccountingFileImportConflictException exception) {
        String code = switch (exception.getReason()) {
            case SOURCE_NOT_AVAILABLE -> "SOURCE_NOT_AVAILABLE";
            case IDEMPOTENCY_MISMATCH -> "IDEMPOTENCY_CONFLICT";
            case PERIOD_OVERLAP -> "PERIOD_OVERLAP";
            case PUBLISHED_DATA_CONFLICT -> "PUBLISHED_DATA_CONFLICT";
            case PREVIEW_FILE_MISMATCH -> "PREVIEW_FILE_MISMATCH";
        };
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                code,
                "A importação conflita com o estado atual da fonte contábil."));
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
