package com.finemp.finance.entry;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** Current homologated target: root plus later unpaid occurrences, not a positional suffix. */
@Repository
@ConditionalOnProperty(prefix="finemp.finance.entries",name="enabled",havingValue="true")
@Transactional(propagation=Propagation.MANDATORY)
public class EntryDeletionSelection {
    private final JdbcTemplate jdbc;
    public EntryDeletionSelection(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    List<Target> lock(UUID company,UUID entry) {
        return jdbc.query("""
                WITH root AS (
                    SELECT e.id,e.due_date,m.series_id FROM financial_entries e
                    LEFT JOIN financial_entry_series_members m ON m.company_id=e.company_id AND m.entry_id=e.id
                    WHERE e.company_id=? AND e.id=?
                ), candidates AS (
                    SELECT id FROM root
                    UNION ALL
                    SELECT e.id FROM root r
                    JOIN financial_entry_series_members m ON m.company_id=? AND m.series_id=r.series_id
                    JOIN financial_entries e ON e.company_id=m.company_id AND e.id=m.entry_id
                    WHERE e.id<>r.id AND e.payment_date IS NULL AND e.due_date>r.due_date
                )
                SELECT e.id,e.version,m.id AS membership_id,m.version AS membership_version,m.occurrence_number
                FROM financial_entries e JOIN candidates c ON c.id=e.id
                LEFT JOIN financial_entry_series_members m ON m.company_id=e.company_id AND m.entry_id=e.id
                WHERE e.company_id=? ORDER BY e.id FOR UPDATE OF e
                """,(row,index)->new Target(row.getObject("id",UUID.class),row.getLong("version"),row.getObject("membership_id",UUID.class),
                        row.getObject("membership_version",Long.class),row.getObject("occurrence_number",Integer.class)),company,entry,company,company);
    }
    void removeMemberships(UUID company,List<Target> targets) {
        for(var target:targets)if(target.membershipId()!=null && jdbc.update("DELETE FROM financial_entry_series_members WHERE company_id=? AND id=? AND entry_id=? AND version=?",
                company,target.membershipId(),target.entryId(),target.membershipVersion())!=1)
            throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.STALE_VERSION);
    }
    record Target(UUID entryId,long version,UUID membershipId,Long membershipVersion,Integer number) { }
}
