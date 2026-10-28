package com.finemp.finance.partner;

import jakarta.validation.constraints.*;

public record BusinessPartnerRequest(@NotNull BusinessPartnerProfile profile,@PositiveOrZero Long expectedVersion) {
    @Override public String toString() {return "BusinessPartnerRequest[redacted]";}
}
