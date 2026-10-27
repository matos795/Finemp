package com.finemp.accounting.source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AccountingSourceRepository
        extends JpaRepository<AccountingSource, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select source from AccountingSource source where source.id = :id")
    java.util.Optional<AccountingSource> findLockedForSyncById(@Param("id") UUID id);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "company")
    @Query("select source from AccountingSource source where source.id = :id")
    java.util.Optional<AccountingSource> findForReadingById(@Param("id") UUID id);

    @Query("select source.company.id from AccountingSource source where source.id = :sourceId")
    java.util.Optional<UUID> findCompanyIdById(@Param("sourceId") UUID sourceId);

    @Query("""
            select source from AccountingSource source
            where source.company.id = :companyId
              and (:search = '' or locate(lower(:search), lower(coalesce(source.externalReference, ''))) > 0)
            """)
    Page<AccountingSource> searchByCompany(
            @Param("companyId") UUID companyId,
            @Param("search") String search,
            Pageable pageable);
}
