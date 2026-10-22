package com.finemp.accounting.classification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DreClassificationController.class)
class DreClassificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DreClassificationRepository dreClassificationRepository;

    @Test
    void shouldReturnGlobalDreClassificationCatalog() throws Exception {

        DreClassification classification = new DreClassification(
                "EBITDA",
                "RECEITA BRUTA",
                "SERVIÇOS");

        when(dreClassificationRepository.findAll())
                .thenReturn(List.of(classification));

        mockMvc.perform(
                get("/api/dre-classifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dreClassification")
                        .value("EBITDA"))
                .andExpect(jsonPath("$[0].dreOpenClassification")
                        .value("RECEITA BRUTA"))
                .andExpect(jsonPath("$[0].ebitdaClassification")
                        .value("SERVIÇOS"));
    }
}