package com.finemp.people;

import java.time.LocalDate;
import java.util.UUID;

/** Sem campos pessoais além do nome: documento, nascimento e salário não existem nesta fatia. */
public record EmployeeResponse(
        UUID id,
        String fullName,
        EmploymentType employmentType,
        UUID jobPositionId,
        String jobPositionTitle,
        UUID resultCenterId,
        String resultCenterName,
        LocalDate hiredOn,
        LocalDate terminatedOn,
        EmployeeStatus status,
        long version) {

    static EmployeeResponse from(Employee employee, LocalDate today) {
        var position = employee.getJobPosition();
        var center = employee.getResultCenter();
        return new EmployeeResponse(
                employee.getId(),
                employee.getFullName(),
                employee.getEmploymentType(),
                position == null ? null : position.getId(),
                position == null ? null : position.getTitle(),
                center == null ? null : center.getId(),
                center == null ? null : center.getName(),
                employee.getHiredOn(),
                employee.getTerminatedOn(),
                employee.statusOn(today),
                employee.getVersion());
    }
}
