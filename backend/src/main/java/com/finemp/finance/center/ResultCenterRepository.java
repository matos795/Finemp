package com.finemp.finance.center;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ResultCenterRepository extends JpaRepository<ResultCenter,UUID> {
    Optional<ResultCenter> findByIdAndCompanyId(UUID id,UUID companyId);
    @Query("select center.code from ResultCenter center where center.company.id=:company and center.id=:id")
    Optional<String> findCurrentCode(@Param("company") UUID company,@Param("id") UUID id);
    Optional<ResultCenter> findByCompanyIdAndCode(UUID companyId,String code);
    boolean existsByCompanyIdAndCode(UUID companyId,String code);
    boolean existsByCompanyIdAndParentId(UUID companyId,UUID parentId);
    long countByCompanyId(UUID companyId);
    @Query("""
            select center from ResultCenter center where center.company.id=:companyId
              and (:search='' or locate(:search,center.searchName)>0 or locate(:search,center.code)>0)
              and (:active is null or center.active=:active)
              and (:root='' or center.code=:root or center.code like concat(:root,'.%'))
            """)
    Page<ResultCenter> search(@Param("companyId") UUID companyId,@Param("search") String search,
            @Param("active") Boolean active,@Param("root") String root,Pageable pageable);
    @Query("select distinct center.parentId from ResultCenter center where center.company.id=:companyId and center.parentId in :ids")
    Set<UUID> parentsWithChildren(@Param("companyId") UUID companyId,@Param("ids") Set<UUID> ids);
}
