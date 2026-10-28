package com.finemp.finance.entry;

import com.finemp.finance.http.ExpectedVersionQuery;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ExpectedVersionQueryTest {
    @Test void versionComesOnlyFromAnExplicitBoundedUrlQuery() {
        assertThat(ExpectedVersionQuery.required("expectedVersion=0")).isZero();assertThat(ExpectedVersionQuery.required("trace=synthetic&expectedVersion=9223372036854775807")).isEqualTo(Long.MAX_VALUE);
        assertThat(ExpectedVersionQuery.required("%65xpectedVersion=2")).isEqualTo(2);
    }
    @Test void absentEmptyDuplicateOrAmbiguousVersionsMustBeRejected() {
        for(var query:new String[]{null,"","other=0","expectedVersion=","expectedVersion=0&expectedVersion=1","expectedVersion=0&%65xpectedVersion=0","expectedVersion=-1","expectedVersion=+1","expectedVersion=1.0","expectedVersion=1e2","expectedVersion=9223372036854775808","expectedVersion=%","expectedVersion="+"0".repeat(20)})
            assertThatThrownBy(()->ExpectedVersionQuery.required(query)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void oversizedQueryMustFailWithoutUnboundedDecoding() {assertThatThrownBy(()->ExpectedVersionQuery.required("x="+"x".repeat(8192)+"&expectedVersion=0")).isInstanceOf(IllegalArgumentException.class);}
}
