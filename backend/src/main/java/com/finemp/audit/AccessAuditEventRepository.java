package com.finemp.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccessAuditEventRepository extends JpaRepository<AccessAuditEvent, UUID> {

    Page<AccessAuditEvent> findByCompanyId(UUID companyId, Pageable pageable);
}
