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

    Optional<Company> findByIdAndClientId(
            UUID id,
            UUID clientId);

    Page<Company> findAllByClientIdAndLegalNameContainingIgnoreCase(
            UUID clientId,
            String search,
            Pageable pageable);
}
