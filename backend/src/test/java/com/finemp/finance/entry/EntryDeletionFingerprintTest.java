package com.finemp.finance.entry;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EntryDeletionFingerprintTest {
    @Test void companyActorTargetAndExpectedVersionMustProduceDistinctDeterministicHashes() {
        var company=UUID.randomUUID();var actor=UUID.randomUUID();var target=UUID.randomUUID();var hash=EntryDeletionFingerprint.of(company,actor,target,0);
        assertThat(hash).matches("[a-f0-9]{64}").isEqualTo(EntryDeletionFingerprint.of(company,actor,target,0));
        assertThat(EntryDeletionFingerprint.of(UUID.randomUUID(),actor,target,0)).isNotEqualTo(hash);
        assertThat(EntryDeletionFingerprint.of(company,UUID.randomUUID(),target,0)).isNotEqualTo(hash);
        assertThat(EntryDeletionFingerprint.of(company,actor,UUID.randomUUID(),0)).isNotEqualTo(hash);
        assertThat(EntryDeletionFingerprint.of(company,actor,target,1)).isNotEqualTo(hash);
    }
}
