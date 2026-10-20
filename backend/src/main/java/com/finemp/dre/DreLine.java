package com.finemp.dre;

import java.math.BigDecimal;

public record DreLine(
        String dreClassification,
        String dreOpenClassification,
        String ebitdaClassification,
        BigDecimal total) {
}