package com.finemp.people;

public record JobPositionRequest(
        String title,
        String description,
        Boolean active,
        Long expectedVersion) { }
