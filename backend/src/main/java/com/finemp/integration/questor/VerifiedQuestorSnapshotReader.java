package com.finemp.integration.questor;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.AccountingSourceType;
import java.sql.SQLException;
import java.util.*;

/** Identity and movements use the same owned, read-only repeatable-read source transaction. */
public class VerifiedQuestorSnapshotReader implements AccountingSourceSnapshotReader, AccountingSourceReader {
    private final QuestorConnectionProvider provider;
    private final FirebirdQuestorCompanyIdentityReader identities;
    private final FirebirdQuestorAccountingReader movements;
    public VerifiedQuestorSnapshotReader(QuestorConnectionProvider provider, FirebirdQuestorCompanyIdentityReader identities,
            FirebirdQuestorAccountingReader movements) {
        this.provider = Objects.requireNonNull(provider); this.identities = Objects.requireNonNull(identities);
        this.movements = Objects.requireNonNull(movements);
    }
    @Override public AccountingSourceType sourceType() { return AccountingSourceType.QUESTOR; }
    @Override public List<ImportedAccountingMovement> readMovements(AccountingReadRequest request) { return readSnapshot(request).movements(); }
    @Override public AccountingSourceSnapshot readSnapshot(AccountingReadRequest request) {
        FirebirdQuestorCompanyIdentityReader.requireCode(request);
        try (var session = QuestorReadOnlySession.open(provider)) {
            var identity = identities.readOn(session.connection(), request);
            if (!identity.matches(request)) throw new AccountingIdentityMismatchException();
            return new AccountingSourceSnapshot(identity, movements.readOn(session.connection(), request.externalCompanyReference(),
                    request.startDate(), request.endDate()));
        } catch (AccountingIdentityMismatchException mismatch) { throw new AccountingIdentityMismatchException(); }
        catch (AccountingIdentityConfigurationException unsupported) { throw new AccountingIdentityConfigurationException(); }
        catch (AccountingReadLimitExceededException overflow) { throw new AccountingReadLimitExceededException(); }
        catch (SQLException | RuntimeException unavailable) { throw new QuestorReadException("Questor snapshot read unavailable.", null); }
    }
}
