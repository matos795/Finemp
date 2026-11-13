package com.finemp.debt;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DebtAgreementRepository extends JpaRepository<DebtAgreement, UUID> {

    boolean existsByCompanyIdAndBusinessPartnerId(UUID companyId, UUID businessPartnerId);

    @EntityGraph(attributePaths = {"businessPartner"})
    Optional<DebtAgreement> findByIdAndCompanyId(UUID id, UUID companyId);

    @EntityGraph(attributePaths = {"businessPartner", "installments"})
    @Query("""
            select distinct debt
              from DebtAgreement debt
             where debt.id = :debtId
               and debt.company.id = :companyId
            """)
    Optional<DebtAgreement> findDetailed(
            @Param("companyId") UUID companyId,
            @Param("debtId") UUID debtId);

    @EntityGraph(attributePaths = {"businessPartner"})
    @Query("""
            select debt
              from DebtAgreement debt
              left join debt.businessPartner partner
             where debt.company.id = :companyId
               and (:origin is null or debt.values.origin = :origin)
               and (
                    :search = ''
                    or lower(debt.values.description) like lower(concat('%', :search, '%'))
                    or lower(coalesce(partner.profile.name, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(partner.profile.tradeName, '')) like lower(concat('%', :search, '%'))
               )
            """)
    Page<DebtAgreement> search(
            @Param("companyId") UUID companyId,
            @Param("origin") DebtOrigin origin,
            @Param("search") String search,
            Pageable pageable);
}
