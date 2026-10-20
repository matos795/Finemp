package com.finemp.dre;

import java.util.List;

public record DreCalculationResult(
        List<DreLine> lines,
        int unclassifiedAccountCount) {

    public DreCalculationResult {
        lines = List.copyOf(lines);
    }
}