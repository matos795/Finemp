package com.finemp.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findAllByAccountingFirmId(UUID accountingFirmId);

    boolean existsByAccountingFirmId(UUID accountingFirmId);

    Optional<Client> findByIdAndAccountingFirmId(
            UUID id,
            UUID accountingFirmId);

    Page<Client> findAllByAccountingFirmIdAndNameContainingIgnoreCase(
            UUID accountingFirmId,
            String search,
            Pageable pageable);
}
