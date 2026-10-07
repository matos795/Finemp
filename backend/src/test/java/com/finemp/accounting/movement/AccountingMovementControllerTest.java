package com.finemp.accounting.movement;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@WebMvcTest(AccountingMovementController.class)
class AccountingMovementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountingMovementQueryService accountingMovementQueryService;

    @Test
    void shouldReturnMovementsForCompanyAndPeriod() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();

        when(accountingMovementQueryService.findByCompanyAndPeriod(
                companyId,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31))).thenReturn(List.of(
                        new AccountingMovementResponse(
                                movementId,
                                LocalDate.of(2026, 7, 15),
                                "Receita de Exemplo",
                                "3.1.01.005.001.009.5396",
                                AccountingMovementSide.CREDIT,
                                new BigDecimal("1250.7500"),
                                "Histórico de exemplo",
                                "entry-123")));

        mockMvc.perform(
                get("/api/companies/{companyId}/accounting-movements", companyId)
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id")
                        .value(movementId.toString()))
                .andExpect(jsonPath("$[0].accountingDate")
                        .value("2026-07-15"))
                .andExpect(jsonPath("$[0].accountDescription")
                        .value("Receita de Exemplo"))
                .andExpect(jsonPath("$[0].side")
                        .value("CREDIT"))
                .andExpect(jsonPath("$[0].amount")
                        .value(1250.75))
                .andExpect(jsonPath("$[0].sourceEntryKey")
                        .value("entry-123"));
    }

    @Test
    void shouldReturnBadRequestForInvalidPeriod() throws Exception {
        UUID companyId = UUID.randomUUID();

        when(accountingMovementQueryService.findByCompanyAndPeriod(
                companyId,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 7, 31))).thenThrow(new InvalidAccountingPeriodException());

        mockMvc.perform(
                get("/api/companies/{companyId}/accounting-movements", companyId)
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isBadRequest());
    }
}