package com.finemp.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    Optional<Contract> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndBusinessPartnerId(UUID companyId, UUID businessPartnerId);

    @EntityGraph(attributePaths = "businessPartner")
    @Query("""
            select contract
              from Contract contract
              join contract.businessPartner partner
             where contract.company.id = :companyId
               and (:active is null or contract.values.active = :active)
               and (
                    :search = ''
                    or lower(contract.values.description) like lower(concat('%', :search, '%'))
                    or lower(partner.profile.name) like lower(concat('%', :search, '%'))
                    or lower(partner.profile.tradeName) like lower(concat('%', :search, '%'))
               )
            """)
    Page<Contract> search(
            @Param("companyId") UUID companyId,
            @Param("search") String search,
            @Param("active") Boolean active,
            Pageable pageable);

    @EntityGraph(attributePaths = "businessPartner")
    @Query("""
            select contract
              from Contract contract
             where contract.company.id = :companyId
               and contract.values.active = true
               and contract.values.startDate <= :monthEnd
               and (contract.values.endDate is null or contract.values.endDate >= :monthStart)
            """)
    Page<Contract> monthlyPreview(
            @Param("companyId") UUID companyId,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd,
            Pageable pageable);

    @Query("""
            select coalesce(sum(contract.values.monthlyAmount), 0)
              from Contract contract
             where contract.company.id = :companyId
               and contract.values.active = true
               and contract.values.direction = :direction
               and contract.values.startDate <= :monthEnd
               and (contract.values.endDate is null or contract.values.endDate >= :monthStart)
            """)
    BigDecimal monthlyPreviewTotal(
            @Param("companyId") UUID companyId,
            @Param("direction") ContractDirection direction,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);
}
