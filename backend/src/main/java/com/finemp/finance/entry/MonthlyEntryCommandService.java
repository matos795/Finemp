package com.finemp.finance.entry;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.company.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix="finemp.finance.entries",name="enabled",havingValue="true")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class MonthlyEntryCommandService {
    private final CompanyPermissionAuthorizationService permissions;
    private final CompanyRepository companies;
    private final FinancialEntryRepository entries;
    private final FinancialEntrySeriesRepository series;
    private final EntrySeriesMembershipRepository members;
    private final EntryWriteTransactionGuard guard;
    private final EntryCommandReferences references;
    private final EntrySeriesCommandJournal journal;
    public MonthlyEntryCommandService(CompanyPermissionAuthorizationService permissions,CompanyRepository companies,FinancialEntryRepository entries,
            FinancialEntrySeriesRepository series,EntrySeriesMembershipRepository members,EntryWriteTransactionGuard guard,
            EntryCommandReferences references,EntrySeriesCommandJournal journal) {
        this.permissions=permissions;this.companies=companies;this.entries=entries;this.series=series;this.members=members;this.guard=guard;this.references=references;this.journal=journal;
    }
    public EntrySeriesCommandReceipt create(UUID actor,UUID company,UUID command,int quantity,FinancialEntryCommandData data) {
        requireManage(actor,company);guard.requireReadCommitted();var owner=companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);requireManage(actor,company);
        if(command==null || data==null)throw new IllegalArgumentException("Comando mensal inválido.");
        var definition=new EntrySeriesDefinition(SeriesMode.MONTHLY,quantity);var fingerprint=EntrySeriesCommandFingerprint.monthly(company,actor,quantity,data);
        var replay=journal.find(company,actor,command);
        if(replay.isPresent()) {
            if(!replay.get().fingerprint().equals(fingerprint))throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.IDEMPOTENCY_MISMATCH);
            return replay.get().receipt();
        }
        var calendar=EntrySeriesCalendar.generate(new EntrySeriesCalendar.Input(SeriesMode.MONTHLY,quantity,data.documentDate(),data.entryDate(),data.competence(),data.dueDate(),data.paymentDate()));
        references.validate(company,data,null);
        var planned=new ArrayList<FinancialEntry>(quantity);
        for(var occurrence:calendar)planned.add(new FinancialEntry(owner,new FinancialEntryValues(data.direction(),data.description(),data.nominalAmount(),
                occurrence.documentDate(),occurrence.entryDate(),occurrence.competence(),occurrence.dueDate(),occurrence.paymentDate(),null,
                data.bankAccountId(),data.categoryId(),data.partnerId(),data.resultCenterId(),data.costBehaviorOverride(),data.paymentMethod(),data.documentNumber(),data.notes()),actor));
        guard.requireEntryPeriodsOpen(company,planned.stream().map(FinancialEntry::getValues).toArray(FinancialEntryValues[]::new));
        var group=series.saveAndFlush(new FinancialEntrySeries(owner,definition,actor));
        var saved=entries.saveAllAndFlush(planned);var links=new ArrayList<EntrySeriesMembership>(quantity);var results=new ArrayList<EntrySeriesCommandReceipt.Item>(quantity);
        for(int index=0;index<saved.size();index++) {
            var row=saved.get(index);links.add(new EntrySeriesMembership(owner,group,row,index+1));results.add(new EntrySeriesCommandReceipt.Item(index+1,row.getId(),row.getVersion()));
        }
        members.saveAllAndFlush(links);var receipt=new EntrySeriesCommandReceipt(company,command,group.getId(),results);
        journal.append(actor,fingerprint,receipt,saved);return receipt;
    }
    private void requireManage(UUID actor,UUID company) {
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
    }
}
