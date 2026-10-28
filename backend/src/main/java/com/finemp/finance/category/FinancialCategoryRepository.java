package com.finemp.finance.category;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FinancialCategoryRepository extends JpaRepository<FinancialCategory,UUID> {
    Optional<FinancialCategory> findByIdAndCompanyId(UUID id,UUID companyId);
    List<FinancialCategory> findAllByCompanyIdAndIdIn(UUID companyId,Set<UUID> ids);
    @Query("select coalesce(max(category.sequenceNumber),0) from FinancialCategory category where category.company.id=:company and category.parentId is null and category.direction=:direction")
    long maxGroupSequence(@Param("company") UUID company,@Param("direction") FinancialDirection direction);
    @Query("select coalesce(max(category.sequenceNumber),0) from FinancialCategory category where category.company.id=:company and category.parentId=:parent")
    long maxSubcategorySequence(@Param("company") UUID company,@Param("parent") UUID parent);
    @Query("""
            select category from FinancialCategory category where category.company.id=:company
              and (:search='' or locate(:search,category.searchName)>0 or locate(:search,category.code)>0)
              and (:direction is null or category.direction=:direction)
              and (:kind is null or category.kind=:kind)
              and (:parent is null or category.parentId=:parent)
            """)
    Page<FinancialCategory> search(@Param("company") UUID company,@Param("search") String search,
            @Param("direction") FinancialDirection direction,@Param("kind") FinancialCategory.Kind kind,@Param("parent") UUID parent,Pageable pageable);
    @Modifying
    @Query("delete from FinancialCategory category where category.company.id=:company and category.parentId=:parent")
    void deleteChildren(@Param("company") UUID company,@Param("parent") UUID parent);
}
