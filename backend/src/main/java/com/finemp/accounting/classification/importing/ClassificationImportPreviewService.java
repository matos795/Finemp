package com.finemp.accounting.classification.importing;

import com.finemp.accounting.classification.AccountingAccountClassificationQueryService;
import com.finemp.accounting.classification.DreClassificationRepository;
import com.finemp.accounting.classification.DreClassificationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class ClassificationImportPreviewService {

    private final PlanoQMappingReader mappingReader;
    private final AccountingAccountClassificationQueryService accountQueryService;
    private final DreClassificationRepository dreClassificationRepository;
    private final ClassificationImportPreviewCalculator calculator;

    public ClassificationImportPreviewService(
            PlanoQMappingReader mappingReader,
            AccountingAccountClassificationQueryService accountQueryService,
            DreClassificationRepository dreClassificationRepository,
            ClassificationImportPreviewCalculator calculator) {

        this.mappingReader = mappingReader;
        this.accountQueryService = accountQueryService;
        this.dreClassificationRepository = dreClassificationRepository;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public List<ClassificationImportPreviewItem> preview(
            UUID companyId,
            InputStream inputStream) {

        var mappings = mappingReader.read(inputStream);

        var accounts = accountQueryService.findAllByCompanyId(
                companyId);

        var classifications = dreClassificationRepository
                .findAll()
                .stream()
                .map(DreClassificationResponse::from)
                .toList();

        return calculator.calculate(
                accounts,
                classifications,
                mappings);
    }
}