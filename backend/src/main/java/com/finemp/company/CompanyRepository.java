package com.finemp.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select company from Company company where company.id = :id")
    Optional<Company> findLockedById(@Param("id") UUID id);

    List<Company> findAllByClientId(UUID clientId);

    /** Companies com CompanyAccessGrant do usuário ativo, já com Client e AccountingFirm. */
    @Query("""
            select company from Company company
            join fetch company.client client
            join fetch client.accountingFirm
            where company.id in (
                select accessGrant.company.id from CompanyAccessGrant accessGrant
                where accessGrant.userAccount.id = :userId
                  and accessGrant.userAccount.active = true)
            """)
    List<Company> findGrantedWithHierarchy(@Param("userId") UUID userId);

    @Query("""
            select company from Company company
            join fetch company.client client
            join fetch client.accountingFirm firm
            where firm.id in :firmIds
            """)
    List<Company> findAllWithHierarchyByAccountingFirmIdIn(@Param("firmIds") java.util.Collection<UUID> firmIds);

    @Query("""
            select company from Company company
            join fetch company.client client
            join fetch client.accountingFirm
            """)
    List<Company> findAllWithHierarchy();

    boolean existsByClientId(UUID clientId);

    Optional<Company> findByIdAndClientId(
            UUID id,
            UUID clientId);

    Page<Company> findAllByClientIdAndLegalNameContainingIgnoreCase(
            UUID clientId,
            String search,
            Pageable pageable);
}
