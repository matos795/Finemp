package com.finemp.billing;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ServiceInvoiceRepository extends JpaRepository<ServiceInvoiceDraft, UUID> {

    boolean existsByCompanyIdAndBusinessPartnerId(UUID companyId, UUID businessPartnerId);

    boolean existsByCompanyIdAndContractId(UUID companyId, UUID contractId);

    @EntityGraph(attributePaths = {"businessPartner", "contract"})
    Optional<ServiceInvoiceDraft> findByIdAndCompanyId(UUID id, UUID companyId);

    @EntityGraph(attributePaths = {"businessPartner", "contract"})
    @Query("""
            select invoice
              from ServiceInvoiceDraft invoice
              join invoice.businessPartner partner
             where invoice.company.id = :companyId
               and invoice.values.competence = :competence
               and (:status is null or invoice.status = :status)
               and (
                    :search = ''
                    or lower(invoice.values.serviceText) like lower(concat('%', :search, '%'))
                    or lower(partner.profile.name) like lower(concat('%', :search, '%'))
                    or lower(partner.profile.tradeName) like lower(concat('%', :search, '%'))
                    or lower(coalesce(invoice.invoiceNumber, '')) like lower(concat('%', :search, '%'))
               )
            """)
    Page<ServiceInvoiceDraft> search(
            @Param("companyId") UUID companyId,
            @Param("competence") LocalDate competence,
            @Param("status") ServiceInvoiceStatus status,
            @Param("search") String search,
            Pageable pageable);
}
