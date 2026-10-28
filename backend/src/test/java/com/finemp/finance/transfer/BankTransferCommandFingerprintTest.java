package com.finemp.finance.transfer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BankTransferCommandFingerprintTest {
    UUID company=UUID.randomUUID(),actor=UUID.randomUUID(),origin=UUID.randomUUID(),destination=UUID.randomUUID();
    BankTransferValues values(String amount,String text) {return new BankTransferValues(origin,destination,LocalDate.of(2026,7,1),new BigDecimal(amount),text);}
    @Test void canonicalEquivalentInputsMustHaveTheSameDeterministicFingerprint() {
        assertThat(BankTransferCommandFingerprint.create(company,actor,values("1.2300"," Description ")))
                .isEqualTo(BankTransferCommandFingerprint.create(company,actor,values("1.23","Description"))).matches("[a-f0-9]{64}");
    }
    @Test void contextActionTargetVersionAndEveryValueMustParticipate() {
        var value=values("1.23","Description");var original=BankTransferCommandFingerprint.create(company,actor,value);
        assertThat(BankTransferCommandFingerprint.create(UUID.randomUUID(),actor,value)).isNotEqualTo(original);
        assertThat(BankTransferCommandFingerprint.create(company,UUID.randomUUID(),value)).isNotEqualTo(original);
        assertThat(BankTransferCommandFingerprint.create(company,actor,values("1.24","Description"))).isNotEqualTo(original);
        assertThat(BankTransferCommandFingerprint.create(company,actor,values("1.23","Description2"))).isNotEqualTo(original);
        assertThat(BankTransferCommandFingerprint.create(company,actor,new BankTransferValues(destination,origin,value.transferDate(),value.amount(),value.description()))).isNotEqualTo(original);
        assertThat(BankTransferCommandFingerprint.create(company,actor,new BankTransferValues(origin,destination,value.transferDate().plusDays(1),value.amount(),value.description()))).isNotEqualTo(original);
        var target=UUID.randomUUID();var deletion=BankTransferCommandFingerprint.delete(company,actor,target,0L);
        assertThat(deletion).isNotEqualTo(original);assertThat(BankTransferCommandFingerprint.delete(company,actor,target,1L)).isNotEqualTo(deletion);
        assertThat(BankTransferCommandFingerprint.delete(company,actor,UUID.randomUUID(),0L)).isNotEqualTo(deletion);
    }
}
