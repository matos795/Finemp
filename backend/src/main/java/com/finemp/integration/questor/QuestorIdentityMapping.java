package com.finemp.integration.questor;

import java.util.Objects;

/** Exact identifiers must come from verified local metadata, never a browser/work payload. */
public record QuestorIdentityMapping(String table, String companyCodeColumn, String cnpjColumn) {
    public QuestorIdentityMapping {
        if (!identifier(table) || !identifier(companyCodeColumn) || !identifier(cnpjColumn)
                || Objects.equals(companyCodeColumn, cnpjColumn))
            throw new IllegalArgumentException("Invalid Questor identity mapping.");
    }
    private static boolean identifier(String value) { return value != null && value.matches("[A-Za-z][A-Za-z0-9_]{0,62}"); }
    String sql() {
        return "SELECT \"" + companyCodeColumn + "\" AS FINEMP_OBS_REF, \"" + cnpjColumn
                + "\" AS FINEMP_OBS_CNPJ FROM \"" + table + "\" WHERE \"" + companyCodeColumn + "\" = ?";
    }
    @Override public String toString() { return "QuestorIdentityMapping[configured]"; }
}
