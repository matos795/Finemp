package com.finemp.accounting.v2read;

import java.util.UUID;

record AccountingChartRow(
        UUID accountId,
        String sourceAccountId,
        String classificationCode,
        String description,
        String accountType,
        String calculationType,
        String balanceNature,
        String balanceControl,
        String showInBalanceSheet,
        String nativeDreCode,
        String nativeDreAccount) {}
