package com.finemp.finance.entry;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface EntrySeriesMembershipRepository extends JpaRepository<EntrySeriesMembership,UUID> {
    Optional<EntrySeriesMembership> findByCompanyIdAndEntryId(UUID companyId,UUID entryId);
    @QueryHints(@jakarta.persistence.QueryHint(name="jakarta.persistence.query.timeout",value="5000"))
    List<EntrySeriesMembership> findByCompanyIdAndSeriesIdOrderByNumber(UUID companyId,UUID seriesId);
}
