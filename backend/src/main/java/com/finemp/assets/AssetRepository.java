package com.finemp.assets;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("""
            select asset from Asset asset
             left join asset.businessPartner partner
             where asset.company.id = :companyId
               and (:search = ''
                    or lower(asset.description) like lower(concat('%', :search, '%'))
                    or lower(coalesce(asset.assetTag, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(asset.brandModel, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(asset.serialNumber, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(asset.location, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(asset.responsible, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(partner.profile.name, '')) like lower(concat('%', :search, '%')))
               and (:status is null or asset.status = :status)
               and (:category is null or asset.category = :category)
            """)
    Page<Asset> search(
            @Param("companyId") UUID companyId,
            @Param("search") String search,
            @Param("status") AssetStatus status,
            @Param("category") AssetCategory category,
            Pageable pageable);

    @Query("""
            select count(asset) > 0 from Asset asset
             where asset.company.id = :companyId
               and asset.assetTag is not null
               and lower(trim(asset.assetTag)) = lower(trim(:assetTag))
               and (:excludeId is null or asset.id <> :excludeId)
            """)
    boolean existsTag(
            @Param("companyId") UUID companyId,
            @Param("assetTag") String assetTag,
            @Param("excludeId") UUID excludeId);
}
