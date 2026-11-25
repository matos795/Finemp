package com.finemp.accounting.canonical.v2;

import com.finemp.accounting.reading.ExternalCompanyIdentity;
import java.util.List;

public record CanonicalAccountingBatch(
        int contractVersion,
        ExternalCompanyIdentity observedIdentity,
        SourceCapabilities capabilities,
        CanonicalChartSnapshot chart,
        List<CanonicalObservedAccountDescriptor> observedAccountDescriptors,
        CanonicalEntriesDataset entries,
        List<CanonicalAccountDailyDebitCreditTotals> dailyTotals,
        List<CanonicalAccountMonthlyDebitCreditTotals> monthlyTotals,
        List<CanonicalOriginCatalogEntry> originCatalog,
        List<CanonicalEntryParticipants> participants,
        List<CanonicalEntryCostAllocation> costAllocations) {

    public static final int VERSION = 2;

    public CanonicalAccountingBatch(
            int contractVersion,
            ExternalCompanyIdentity observedIdentity,
            SourceCapabilities capabilities,
            CanonicalChartSnapshot chart,
            CanonicalEntriesDataset entries,
            List<CanonicalAccountDailyDebitCreditTotals> dailyTotals,
            List<CanonicalAccountMonthlyDebitCreditTotals> monthlyTotals,
            List<CanonicalOriginCatalogEntry> originCatalog,
            List<CanonicalEntryParticipants> participants,
            List<CanonicalEntryCostAllocation> costAllocations) {
        this(
                contractVersion,
                observedIdentity,
                capabilities,
                chart,
                null,
                entries,
                dailyTotals,
                monthlyTotals,
                originCatalog,
                participants,
                costAllocations);
    }

    public CanonicalAccountingBatch {
        observedAccountDescriptors =
                observedAccountDescriptors == null ? null : List.copyOf(observedAccountDescriptors);
        dailyTotals = dailyTotals == null ? null : List.copyOf(dailyTotals);
        monthlyTotals = monthlyTotals == null ? null : List.copyOf(monthlyTotals);
        originCatalog = originCatalog == null ? null : List.copyOf(originCatalog);
        participants = participants == null ? null : List.copyOf(participants);
        costAllocations = costAllocations == null ? null : List.copyOf(costAllocations);
    }
}
