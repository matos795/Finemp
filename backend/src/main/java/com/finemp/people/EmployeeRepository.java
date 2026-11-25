package com.finemp.people;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndResultCenterId(UUID companyId, UUID resultCenterId);

    @Query("""
            select employee from Employee employee
              left join employee.jobPosition position
             where employee.company.id = :companyId
               and (:search = ''
                    or lower(employee.fullName) like lower(concat('%', :search, '%'))
                    or lower(coalesce(position.title, '')) like lower(concat('%', :search, '%')))
               and (:type is null or employee.employmentType = :type)
               and (:positionId is null or position.id = :positionId)
               and (:centerId is null or employee.resultCenter.id = :centerId)
               and (:status is null
                    or (:status = 'ACTIVE' and (employee.terminatedOn is null or employee.terminatedOn > :today))
                    or (:status = 'TERMINATED' and employee.terminatedOn <= :today))
            """)
    Page<Employee> search(
            @Param("companyId") UUID companyId,
            @Param("search") String search,
            @Param("type") EmploymentType type,
            @Param("positionId") UUID positionId,
            @Param("centerId") UUID centerId,
            @Param("status") String status,
            @Param("today") LocalDate today,
            Pageable pageable);
}
