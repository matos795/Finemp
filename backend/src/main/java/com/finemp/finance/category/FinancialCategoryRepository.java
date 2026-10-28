package com.finemp.finance.category;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FinancialCategoryRepository extends JpaRepository<FinancialCategory,UUID> {
    Optional<FinancialCategory> findByIdAndCompanyId(UUID id,UUID companyId);
    @Query("""
            select new com.finemp.finance.category.FinancialCategorySnapshot(
                category.id,category.company.id,category.parentId,category.kind,category.direction,
                category.code,category.name,category.cashActivity,category.dreLine,category.costBehavior,category.version,
                parent.cashActivity,parent.dreLine,parent.costBehavior)
            from FinancialCategory category
            left join FinancialCategory parent on parent.id=category.parentId and parent.company.id=category.company.id
            where category.company.id=:company and category.id=:id
            """)
    Optional<FinancialCategorySnapshot> findSnapshot(@Param("company") UUID company,@Param("id") UUID id);
    @Query("select category.kind from FinancialCategory category where category.company.id=:company and category.id=:id")
    Optional<FinancialCategory.Kind> findCurrentKind(@Param("company") UUID company,@Param("id") UUID id);
    @Query("select coalesce(max(category.sequenceNumber),0) from FinancialCategory category where category.company.id=:company and category.parentId is null and category.direction=:direction")
    long maxGroupSequence(@Param("company") UUID company,@Param("direction") FinancialDirection direction);
    @Query("select coalesce(max(category.sequenceNumber),0) from FinancialCategory category where category.company.id=:company and category.parentId=:parent")
    long maxSubcategorySequence(@Param("company") UUID company,@Param("parent") UUID parent);
    @Query("""
            select new com.finemp.finance.category.FinancialCategorySnapshot(
                category.id,category.company.id,category.parentId,category.kind,category.direction,
                category.code,category.name,category.cashActivity,category.dreLine,category.costBehavior,category.version,
                parentCategory.cashActivity,parentCategory.dreLine,parentCategory.costBehavior)
            from FinancialCategory category
            left join FinancialCategory parentCategory on parentCategory.id=category.parentId and parentCategory.company.id=category.company.id
            where category.company.id=:company
              and (:search='' or locate(:search,category.searchName)>0 or locate(:search,category.code)>0)
              and (:direction is null or category.direction=:direction)
              and (:kind is null or category.kind=:kind)
              and (:parent is null or category.parentId=:parent)
            """)
    Page<FinancialCategorySnapshot> search(@Param("company") UUID company,@Param("search") String search,
            @Param("direction") FinancialDirection direction,@Param("kind") FinancialCategory.Kind kind,@Param("parent") UUID parent,Pageable pageable);
    @Modifying
    @Query("delete from FinancialCategory category where category.company.id=:company and category.parentId=:parent")
    void deleteChildren(@Param("company") UUID company,@Param("parent") UUID parent);
}
