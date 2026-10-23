package com.finemp.accounting.classification.importing;

import com.finemp.accounting.classification.AccountingAccountClassificationResponse;
import com.finemp.accounting.classification.DreClassificationResponse;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component 
public class ClassificationImportPreviewCalculator {

    public List<ClassificationImportPreviewItem> calculate(
            List<AccountingAccountClassificationResponse> accounts,
            List<DreClassificationResponse> classifications,
            List<PlanoQMapping> mappings) {

        Map<String, PlanoQMapping> mappingsByAccountKey = mappings.stream()
                .collect(Collectors.toMap(
                        PlanoQMapping::accountKey,
                        Function.identity()));

        return accounts.stream()
                .map(account -> calculateAccount(
                        account,
                        classifications,
                        mappingsByAccountKey))
                .toList();
    }

    private ClassificationImportPreviewItem calculateAccount(
            AccountingAccountClassificationResponse account,
            List<DreClassificationResponse> classifications,
            Map<String, PlanoQMapping> mappingsByAccountKey) {

        PlanoQMapping mapping = mappingsByAccountKey.get(
                account.accountKey());

        if (mapping == null) {
            return new ClassificationImportPreviewItem(
                    account.accountId(),
                    account.accountKey(),
                    account.description(),
                    ClassificationImportStatus.MAPPING_NOT_FOUND,
                    account.classificationId(),
                    null,
                    null,
                    null,
                    null);
        }

        DreClassificationResponse proposed = classifications.stream()
                .filter(classification -> matches(
                        classification,
                        mapping))
                .findFirst()
                .orElse(null);

        if (proposed == null) {
            return new ClassificationImportPreviewItem(
                    account.accountId(),
                    account.accountKey(),
                    account.description(),
                    ClassificationImportStatus.CLASSIFICATION_NOT_FOUND,
                    account.classificationId(),
                    null,
                    mapping.dreClassification(),
                    mapping.dreOpenClassification(),
                    mapping.ebitdaClassification());
        }

        ClassificationImportStatus status = resolveStatus(
                account,
                proposed);

        return new ClassificationImportPreviewItem(
                account.accountId(),
                account.accountKey(),
                account.description(),
                status,
                account.classificationId(),
                proposed.id(),
                proposed.dreClassification(),
                proposed.dreOpenClassification(),
                proposed.ebitdaClassification());
    }

    private boolean matches(
            DreClassificationResponse classification,
            PlanoQMapping mapping) {

        return classification
                .dreClassification()
                .equals(mapping.dreClassification())
                && classification
                        .dreOpenClassification()
                        .equals(mapping.dreOpenClassification())
                && classification
                        .ebitdaClassification()
                        .equals(mapping.ebitdaClassification());
    }

    private ClassificationImportStatus resolveStatus(
            AccountingAccountClassificationResponse account,
            DreClassificationResponse proposed) {

        if (account.classificationId() == null) {
            return ClassificationImportStatus.READY;
        }

        if (account.classificationId()
                .equals(proposed.id())) {

            return ClassificationImportStatus.ALREADY_CLASSIFIED;
        }

        return ClassificationImportStatus.CONFLICT;
    }
}