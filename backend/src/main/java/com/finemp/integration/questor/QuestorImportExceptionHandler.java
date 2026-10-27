package com.finemp.integration.questor;

import com.finemp.accounting.reading.AccountingReaderUnavailableException;
import com.finemp.accounting.reading.AccountingReadLimitExceededException;
import com.finemp.administration.SaasResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = QuestorAccountingImportController.class)
public class QuestorImportExceptionHandler {
    @ExceptionHandler(AccountingReadLimitExceededException.class)
    ProblemDetail resourceLimit() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Leitura excede o limite de recursos; solicite período menor.");
        problem.setProperty("code", "RESOURCE_LIMIT"); return problem;
    }
    @ExceptionHandler(QuestorSourceStateException.class)
    ProblemDetail invalidSource() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Fonte contábil indisponível para importação.");
    }
    @ExceptionHandler({QuestorReadException.class, AccountingReaderUnavailableException.class})
    ProblemDetail readFailed() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Não foi possível ler a fonte contábil. Dados já importados foram preservados.");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Contexto de leitura contábil inválido.");
    }
    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail notFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Fonte contábil não encontrada.");
    }
}
