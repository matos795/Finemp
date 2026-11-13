package com.finemp.finance.partner;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BusinessPartnerRepository extends JpaRepository<BusinessPartner,UUID> {
    Optional<BusinessPartner> findByIdAndCompanyId(UUID id,UUID companyId);
    boolean existsByCompanyIdAndProfileDocument(UUID companyId,String document);
    boolean existsByCompanyIdAndProfileDocumentAndIdNot(UUID companyId,String document,UUID id);
    @Query("""
            select partner from BusinessPartner partner
             where partner.company.id=:companyId
               and partner.profile.active=true
               and (:search='' or lower(partner.profile.name) like lower(concat('%',:search,'%'))
                    or lower(partner.profile.tradeName) like lower(concat('%',:search,'%')))
            """)
    Page<BusinessPartner> searchActiveLookup(
            @Param("companyId") UUID companyId,
            @Param("search") String search,
            Pageable pageable);
    @Query("""
            select partner from BusinessPartner partner
             where partner.company.id=:companyId
               and (:search='' or lower(partner.profile.name) like lower(concat('%',:search,'%'))
                    or lower(partner.profile.tradeName) like lower(concat('%',:search,'%')))
            """)
    Page<BusinessPartner> searchLookup(
            @Param("companyId") UUID companyId,
            @Param("search") String search,
            Pageable pageable);
    @Query("""
            select partner from BusinessPartner partner where partner.company.id=:companyId
              and (:search='' or locate(:search,partner.searchText)>0
                or (:documentSearch<>'' and locate(:documentSearch,partner.profile.document)>0))
              and partner.profile.role in :roles
              and (:active is null or partner.profile.active=:active)
            """)
    Page<BusinessPartner> search(@Param("companyId") UUID companyId,@Param("search") String search,
            @Param("documentSearch") String documentSearch,@Param("roles") Set<PartnerRole> roles,@Param("active") Boolean active,Pageable pageable);
}
