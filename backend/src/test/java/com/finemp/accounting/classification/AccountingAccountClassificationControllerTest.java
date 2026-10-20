package com.finemp.accounting.classification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountingAccountClassificationController.class)
class AccountingAccountClassificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountingAccountClassificationQueryService queryService;

    @MockitoBean
    private AccountingAccountClassificationService classificationService;

    @Test
    void shouldReturnClassifiedAndPendingAccountsForCompany() throws Exception {

        UUID companyId = UUID.randomUUID();
        UUID classifiedAccountId = UUID.randomUUID();
        UUID pendingAccountId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID classificationId = UUID.randomUUID();

        when(queryService.findAllByCompanyId(companyId))
                .thenReturn(List.of(
                        new AccountingAccountClassificationResponse(
                                classifiedAccountId,
                                sourceId,
                                "3.1.01.100",
                                "Receita de serviços",
                                "CLASSIFIED",
                                classificationId,
                                "EBITDA",
                                "RECEITA BRUTA",
                                "SERVIÇOS"),
                        new AccountingAccountClassificationResponse(
                                pendingAccountId,
                                sourceId,
                                "4.1.01.200",
                                "Energia elétrica",
                                "PENDING",
                                null,
                                null,
                                null,
                                null)));

        mockMvc.perform(
                get(
                        "/api/companies/{companyId}/accounting-accounts/classifications",
                        companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].accountId")
                        .value(classifiedAccountId.toString()))
                .andExpect(jsonPath("$[0].status")
                        .value("CLASSIFIED"))
                .andExpect(jsonPath("$[0].classificationId")
                        .value(classificationId.toString()))
                .andExpect(jsonPath("$[0].dreClassification")
                        .value("EBITDA"))
                .andExpect(jsonPath("$[1].accountId")
                        .value(pendingAccountId.toString()))
                .andExpect(jsonPath("$[1].status")
                        .value("PENDING"))
                .andExpect(jsonPath("$[1].classificationId")
                        .doesNotExist());
    }

    @Test
    void shouldClassifyAccountingAccount() throws Exception {

        UUID companyId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID classificationId = UUID.randomUUID();

        mockMvc.perform(
                put(
                        "/api/companies/{companyId}/accounting-accounts/{accountId}/classification",
                        companyId,
                        accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "classificationId": "%s"
                                }
                                """.formatted(classificationId)))
                .andExpect(status().isNoContent());

        verify(classificationService)
                .classifyAccount(
                        companyId,
                        accountId,
                        classificationId);
    }
}