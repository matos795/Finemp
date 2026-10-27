package com.finemp.accounting.sync.control;

import com.finemp.accounting.sync.SyncRunMode;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RequestSyncRun(@NotNull SyncRunMode mode, @NotNull LocalDate startDate, @NotNull LocalDate endDate) { }

