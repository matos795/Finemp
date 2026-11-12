package com.finemp.contracts;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    Optional<Contract> findByIdAndCompanyId(UUID id, UUID companyId);

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
}
