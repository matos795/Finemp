package com.finemp.accounting.classification.importing;

import com.finemp.accounting.classification.AccountingAccountClassificationService;
import com.finemp.accounting.classification.DreClassificationCatalogService;
import com.finemp.accounting.classification.DreClassificationKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class ClassificationImportApplyService {

    private final PlanoQMappingReader mappingReader;
    private final ClassificationImportPreviewService previewService;
    private final DreClassificationCatalogService catalogService;
    private final AccountingAccountClassificationService classificationService;

    public ClassificationImportApplyService(
            PlanoQMappingReader mappingReader,
            ClassificationImportPreviewService previewService,
            DreClassificationCatalogService catalogService,
            AccountingAccountClassificationService classificationService) {

        this.mappingReader = mappingReader;
        this.previewService = previewService;
        this.catalogService = catalogService;
        this.classificationService = classificationService;
    }

    @Transactional
    public ClassificationImportApplyResponse apply(
            UUID companyId,
            InputStream inputStream) {

        List<PlanoQMapping> mappings = mappingReader.read(inputStream);

        var initialPreview = previewService.preview(
                companyId,
                mappings);

        var missingCatalogKeys = initialPreview.stream()
                .filter(item -> item.status() == ClassificationImportStatus.CLASSIFICATION_NOT_FOUND)
                .map(item -> new DreClassificationKey(
                        item.dreClassification(),
                        item.dreOpenClassification(),
                        item.ebitdaClassification()))
                .distinct()
                .toList();

        int createdCatalogClassifications = catalogService.createMissing(
                missingCatalogKeys);

        /*
         * Recalculamos.
         *
         * As categorias que antes não existiam agora devem
         * transformar essas contas em READY.
         */
        var finalPreview = previewService.preview(
                companyId,
                mappings);

        int classifiedAccounts = 0;

        for (ClassificationImportPreviewItem item : finalPreview) {

            if (item.status() != ClassificationImportStatus.READY) {
                continue;
            }

            classificationService.classifyAccount(
                    companyId,
                    item.accountId(),
                    item.proposedClassificationId());

            classifiedAccounts++;
        }

        return new ClassificationImportApplyResponse(
                createdCatalogClassifications,
                classifiedAccounts,
                count(
                        finalPreview,
                        ClassificationImportStatus.ALREADY_CLASSIFIED),
                count(
                        finalPreview,
                        ClassificationImportStatus.MAPPING_NOT_FOUND),
                count(
                        finalPreview,
                        ClassificationImportStatus.CONFLICT),
                count(
                        finalPreview,
                        ClassificationImportStatus.CLASSIFICATION_NOT_FOUND));
    }

    private int count(
            List<ClassificationImportPreviewItem> items,
            ClassificationImportStatus status) {

        return (int) items.stream()
                .filter(item -> item.status() == status)
                .count();
    }
}