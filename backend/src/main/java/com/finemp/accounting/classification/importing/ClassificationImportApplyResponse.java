package com.finemp.accounting.classification.importing;

public record ClassificationImportApplyResponse(
        int createdCatalogClassifications,
        int classifiedAccounts,
        int alreadyClassifiedAccounts,
        int mappingNotFoundAccounts,
        int conflicts,
        int unresolvedClassifications) {
}