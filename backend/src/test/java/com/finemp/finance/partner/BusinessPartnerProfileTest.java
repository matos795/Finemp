package com.finemp.finance.partner;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BusinessPartnerProfileTest {
    static BusinessPartnerProfile profile(String document,PartnerPersonType person,PartnerRole role) {
        return new BusinessPartnerProfile(person,document," Synthetic Partner "," Trade ",role,"","",""," São Paulo ","spx","","","","","","35-01","","","",true);
    }
    @Test void shouldNormalizeObservedFieldsAndRemoveIndividualTradeName() {
        var individual=profile("98765432100",PartnerPersonType.INDIVIDUAL,PartnerRole.BOTH);
        assertThat(individual.name()).isEqualTo("Synthetic Partner");
        assertThat(individual.tradeName()).isEmpty();
        assertThat(individual.stateCode()).isEqualTo("SP");
        assertThat(individual.municipalityCode()).isEqualTo("3501");
        assertThat(individual.withActive(false).active()).isFalse();
        assertThat(individual.toString()).doesNotContain(individual.document(),individual.name());
        assertThat(BusinessPartner.searchKey(" SÃO PAULO ")).isEqualTo("sao paulo");
    }
    @Test void shouldRequireRoleAndExplicitStateOnManualRegistration() {
        assertThatThrownBy(()->profile("12ABC34501DE35",PartnerPersonType.LEGAL_ENTITY,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->profile("",PartnerPersonType.LEGAL_ENTITY,PartnerRole.CUSTOMER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BusinessPartner(null,profile("12ABC34501DE35",PartnerPersonType.LEGAL_ENTITY,PartnerRole.CUSTOMER))).isInstanceOf(IllegalArgumentException.class);
    }
}
