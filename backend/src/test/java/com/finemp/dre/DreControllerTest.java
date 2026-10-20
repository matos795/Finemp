package com.finemp.dre;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DreController.class)
class DreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DreCalculationService dreCalculationService;

    @Test
    void shouldReturnDreForCompanyAndPeriod() throws Exception {

        UUID companyId = UUID.randomUUID();

        when(dreCalculationService.calculate(
                companyId,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)))
                .thenReturn(
                        new DreCalculationResult(
                                List.of(
                                        new DreLine(
                                                "EBITDA",
                                                "RECEITA BRUTA",
                                                "SERVIÇOS",
                                                new BigDecimal("125000.00")),
                                        new DreLine(
                                                "EBITDA",
                                                "DESPESAS",
                                                "ENERGIA",
                                                new BigDecimal("-15000.00"))),
                                2));

        mockMvc.perform(
                get("/api/companies/{companyId}/dre", companyId)
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()")
                        .value(2))
                .andExpect(jsonPath("$.lines[0].dreClassification")
                        .value("EBITDA"))
                .andExpect(jsonPath("$.lines[0].dreOpenClassification")
                        .value("RECEITA BRUTA"))
                .andExpect(jsonPath("$.lines[0].ebitdaClassification")
                        .value("SERVIÇOS"))
                .andExpect(jsonPath("$.lines[0].total")
                        .value(125000.00))
                .andExpect(jsonPath("$.unclassifiedAccountCount")
                        .value(2));
    }
}