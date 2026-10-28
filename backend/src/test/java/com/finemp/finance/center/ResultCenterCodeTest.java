package com.finemp.finance.center;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResultCenterCodeTest {
    @Test void shouldPreserveObservedNumericSegmentsAndDeriveTheExactParent() {
        var code=new ResultCenterCode(" 1 . 01 . 2 ");
        assertThat(code.value()).isEqualTo("1.01.2");
        assertThat(code.parentCode()).isEqualTo("1.01");
        assertThat(new ResultCenterCode("1").parentCode()).isNull();
    }
    @Test void subtreeMustUseASegmentBoundaryInsteadOfSharingAPrefix() {
        var parent=new ResultCenterCode("1.1");
        assertThat(new ResultCenterCode("1.1").isWithin(parent)).isTrue();
        assertThat(new ResultCenterCode("1.1.2").isWithin(parent)).isTrue();
        assertThat(new ResultCenterCode("1.10").isWithin(parent)).isFalse();
        assertThat(new ResultCenterCode("1.01").isWithin(parent)).isFalse();
    }
    @Test void shouldSortWholeBranchesNumericallyInsteadOfLexicographically() {
        var codes=List.of("10","1.10","1.2","2","1","1.2.1").stream().map(ResultCenterCode::new)
                .sorted(Comparator.comparing(ResultCenterCode::sortKey)).map(ResultCenterCode::value).toList();
        assertThat(codes).containsExactly("1","1.2","1.2.1","1.10","2","10");
    }
    @Test void shouldRejectMalformedOrUnboundedCodes() {
        for(String code:new String[]{"","1.",".1","1..2","a.1","-1","1/2","123456789012345678901"}) {
            assertThatThrownBy(()->new ResultCenterCode(code)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(()->new ResultCenterCode(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
