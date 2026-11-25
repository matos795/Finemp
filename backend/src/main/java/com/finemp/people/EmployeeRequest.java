package com.finemp.people;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeRequest(
        String fullName,
        EmploymentType employmentType,
        UUID jobPositionId,
        UUID resultCenterId,
        LocalDate hiredOn,
        LocalDate terminatedOn,
        Long expectedVersion) { }
