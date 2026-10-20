package com.finemp.accounting.classification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "dre_classifications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dre_classifications_levels", columnNames = {
                "dre_classification",
                "dre_open_classification",
                "ebitda_classification"
        })
})
public class DreClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dre_classification", nullable = false)
    private String dreClassification;

    @Column(name = "dre_open_classification", nullable = false)
    private String dreOpenClassification;

    @Column(name = "ebitda_classification", nullable = false)
    private String ebitdaClassification;

    protected DreClassification() {
    }

    public DreClassification(
            String dreClassification,
            String dreOpenClassification,
            String ebitdaClassification) {
        this.dreClassification = dreClassification;
        this.dreOpenClassification = dreOpenClassification;
        this.ebitdaClassification = ebitdaClassification;
    }

    public UUID getId() {
        return id;
    }

    public String getDreClassification() {
        return dreClassification;
    }

    public String getDreOpenClassification() {
        return dreOpenClassification;
    }

    public String getEbitdaClassification() {
        return ebitdaClassification;
    }
}