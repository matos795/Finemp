package com.finemp.finance.partner;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PartnerDocumentTest {
    @Test void shouldNormalizeAndValidateSyntheticCpf() {
        assertThat(PartnerDocument.requireValid("987.654.321-00",PartnerPersonType.INDIVIDUAL)).isEqualTo("98765432100");
    }
    @Test void shouldAcceptAlphanumericCnpjWithTheHomologatedAsciiChecksum() {
        assertThat(PartnerDocument.requireValid("12.abc.345/01de-35",PartnerPersonType.LEGAL_ENTITY)).isEqualTo("12ABC34501DE35");
        assertThat(PartnerDocument.requireValid("98ZYX76543WV39",PartnerPersonType.LEGAL_ENTITY)).isEqualTo("98ZYX76543WV39");
    }
    @Test void shouldRejectRepeatedIncorrectWrongTypeOrMissingDocumentsWithoutEchoingThem() {
        for(var value:new String[]{"11111111111","98765432101","","12ABC34501DE35"}) {
            assertThatThrownBy(()->PartnerDocument.requireValid(value,PartnerPersonType.INDIVIDUAL))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Documento do parceiro inválido.");
        }
        for(var value:new String[]{"AAAAAAAAAAAAAA","12ABC34501DE36","98765432100","00000000000000"}) {
            assertThatThrownBy(()->PartnerDocument.requireValid(value,PartnerPersonType.LEGAL_ENTITY)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(()->PartnerDocument.requireValid(null,PartnerPersonType.LEGAL_ENTITY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->PartnerDocument.requireValid("98765432100",null)).isInstanceOf(IllegalArgumentException.class);
    }
}
