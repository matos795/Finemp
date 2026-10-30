package com.finemp.finance.reporting;

import com.finemp.access.CompanyPermissionAuthorizationService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresentationOptionalDreTest {
    @Test void existingPresentationStillStartsWithoutOperationalDreOptIn(){
        new ApplicationContextRunner().withPropertyValues("finemp.finance.presentation.enabled=true","finemp.finance.aging.enabled=true","finemp.finance.daily-cash.enabled=true","finemp.finance.monthly-cash.enabled=true")
                .withBean(CompanyPermissionAuthorizationService.class,()->mock(CompanyPermissionAuthorizationService.class))
                .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class))
                .withBean(DailyCashTotalsService.class,()->mock(DailyCashTotalsService.class))
                .withBean(MonthlyCashTotalsService.class,()->mock(MonthlyCashTotalsService.class))
                .withBean(OpenTitlesAgingService.class,()->mock(OpenTitlesAgingService.class))
                .withUserConfiguration(FinancialPresentationSnapshotService.class,OperationalDreMonthlyReadService.class)
                .run(context->{assertThat(context).hasSingleBean(FinancialPresentationSnapshotService.class).doesNotHaveBean(OperationalDreMonthlyReadService.class);});
    }
    @Test void unavailableDreIsOmittedInsteadOfPublishingFalseZeroTotals(){
        var snapshot=new FinancialPresentationSnapshotService.Snapshot(1,true,UUID.randomUUID(),"2026-07",LocalDate.of(2026,7,31),OpenTitlesAgingService.Scope.ALL,null,null,null,null,null,null);
        var json=tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(snapshot);
        assertThat(json.has("operationalDre")).isFalse();assertThat(json.get("partial").asBoolean()).isTrue();assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
    }
}
