package com.finemp.accounting.movement;

import com.finemp.accounting.canonical.persistence.AccountingEntrySourceMetadata;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Leitura dos metadados v2 de origem para a página de movimentos (#474). */
interface AccountingMovementSourceMetadataRepository extends Repository<AccountingEntrySourceMetadata, UUID> {

    /**
     * Candidatos por fonte e chave; o casamento exato do par (fonte, chave) é
     * feito pelo chamador, para nunca atribuir metadado de outra fonte.
     */
    @Query("""
            select metadata from AccountingEntrySourceMetadata metadata
            where metadata.accountingSourceId in :sourceIds
              and metadata.sourceEntryKey in :entryKeys
            """)
    List<AccountingEntrySourceMetadata> findCandidates(
            @Param("sourceIds") Collection<UUID> sourceIds,
            @Param("entryKeys") Collection<String> entryKeys);
}
