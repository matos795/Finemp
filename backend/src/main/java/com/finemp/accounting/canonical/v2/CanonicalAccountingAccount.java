package com.finemp.accounting.canonical.v2;

public record CanonicalAccountingAccount(
        String sourceAccountId,
        String classificationCode,
        String description,
        SourceAccountCodes codes) implements CanonicalAccountDescriptor {
}
