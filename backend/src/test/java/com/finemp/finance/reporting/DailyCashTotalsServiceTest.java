package com.finemp.finance.reporting;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyCashTotalsServiceTest {
    final CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    final NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
    final DailyCashTotalsService service=new DailyCashTotalsService(permissions,jdbc);final UUID user=UUID.randomUUID(),company=UUID.randomUUID();
    @Test void unauthorizedNeverQueriesFinancialData(){assertThatThrownBy(()->service.report(user,company,"2024-02")).isInstanceOf(OrganizationAccessDeniedException.class);verifyNoInteractions(jdbc);}
    @Test void emptyLeapMonthHasEveryDayAndInvalidMonthsFail(){
        when(permissions.hasFreshPermission(user,company,CompanyPermission.FINANCIAL_ENTRY_VIEW)).thenReturn(true);
        var result=service.report(user,company,"2024-02");assertThat(result.days()).hasSize(29);assertThat(result.days().getLast().date().toString()).isEqualTo("2024-02-29");assertThat(result.days()).allSatisfy(day->assertThat(day.openIncome()).isEqualByComparingTo("0.00"));
        for(String invalid:new String[]{"0000-01","2024-13","2024-2","2024-02x"})assertThatThrownBy(()->service.report(user,company,invalid)).isInstanceOf(IllegalArgumentException.class);
    }
}
