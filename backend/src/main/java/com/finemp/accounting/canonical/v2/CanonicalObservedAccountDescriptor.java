package com.finemp.accounting.canonical.v2;

public record CanonicalObservedAccountDescriptor(
        String sourceAccountId,
        String classificationCode,
        String description) implements CanonicalAccountDescriptor {
}
