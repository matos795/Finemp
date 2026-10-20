package com.finemp.integration.questor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = QuestorAccountingImportController.class, properties = "finemp.integration.questor.enabled=true")
class QuestorAccountingImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestorAccountingImportService importService;

    @Test
    void shouldImportQuestorMovementsForAccountingSourceAndPeriod()
            throws Exception {

        UUID accountingSourceId = UUID.randomUUID();

        mockMvc.perform(
                post(
                        "/api/accounting-sources/{accountingSourceId}/questor-import",
                        accountingSourceId)
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isNoContent());

        verify(importService)
                .importMovements(
                        accountingSourceId,
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31));
    }
}