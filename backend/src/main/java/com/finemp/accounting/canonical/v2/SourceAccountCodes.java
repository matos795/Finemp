package com.finemp.accounting.canonical.v2;

public record SourceAccountCodes(
        String accountType,
        String calculationType,
        String balanceNature,
        String balanceControl,
        String showInBalanceSheet,
        String nativeDreCode,
        String nativeDreAccount) {
}
