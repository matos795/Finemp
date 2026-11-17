package com.finemp.integration.questor;

import java.util.*;

/** Exact identifiers must come from verified local metadata, never a browser/work payload. */
public record QuestorIdentityMapping(
        String table,
        String companyCodeColumn,
        String cnpjColumn,
        String establishmentColumn,
        Integer establishmentValue) {

    public QuestorIdentityMapping(String table, String companyCodeColumn, String cnpjColumn) {
        this(table, companyCodeColumn, cnpjColumn, null, null);
    }

    public QuestorIdentityMapping {
        boolean establishmentConfigured = establishmentColumn != null || establishmentValue != null;
        if (!identifier(table) || !identifier(companyCodeColumn) || !identifier(cnpjColumn)
                || Objects.equals(companyCodeColumn, cnpjColumn)
                || (establishmentConfigured && (!identifier(establishmentColumn) || establishmentValue == null || establishmentValue < 1))
                || Objects.equals(establishmentColumn, companyCodeColumn)
                || Objects.equals(establishmentColumn, cnpjColumn))
            throw new IllegalArgumentException("Invalid Questor identity mapping.");
    }

    boolean hasEstablishmentFilter() {
        return establishmentColumn != null;
    }

    private static boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9_]{0,62}");
    }

    String sql() {
        String base = "SELECT \"" + companyCodeColumn + "\" AS FINEMP_OBS_REF, \"" + cnpjColumn
                + "\" AS FINEMP_OBS_CNPJ FROM \"" + table + "\" WHERE \"" + companyCodeColumn + "\" = ?";
        return hasEstablishmentFilter() ? base + " AND \"" + establishmentColumn + "\" = ?" : base;
    }

    @Override public String toString() { return "QuestorIdentityMapping[configured]"; }
}
