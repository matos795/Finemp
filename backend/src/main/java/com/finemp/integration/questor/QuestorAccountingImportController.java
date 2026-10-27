package com.finemp.integration.questor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@Profile("!prod")
@RequestMapping("/api/accounting-sources")
@ConditionalOnExpression("${finemp.integration.questor.enabled:false} && !${finemp.sync.executor.enabled:false}")
public class QuestorAccountingImportController {

    private final QuestorAccountingImportService importService;

    public QuestorAccountingImportController(
            QuestorAccountingImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/{accountingSourceId}/questor-import")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void importMovements(
            @PathVariable UUID accountingSourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        importService.importMovements(
                accountingSourceId,
                startDate,
                endDate);
    }
}
