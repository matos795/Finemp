package com.finemp.finance.entry;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
import com.finemp.security.UserAccountPrincipal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"finemp.finance.entries.enabled=true","finemp.finance.entries.commands.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MonthlyEntryHttpIntegrationTest {
    @Autowired FinancialEntryCommandService individual;@Autowired EntrySettlementCommandService settlement;@Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;@Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;@Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
    Company owner,other;UserAccount manager,viewer;UUID bank,category,foreignBank;
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();final JsonMapper json=JsonMapper.builder().build();
    @BeforeEach void prepare() {
        owner=company("Owner","97979797000197");other=company("Other","98989898000198");manager=createUser("Manager");viewer=createUser("Viewer");
        grants.saveAndFlush(new CompanyAccessGrant(manager,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE)));
        grants.saveAndFlush(new CompanyAccessGrant(viewer,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW)));
        bank=bank(owner);foreignBank=bank(other);var root=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.EXPENSE,1,"Synthetic Monthly HTTP Group"));category=categories.saveAndFlush(FinancialCategory.subcategory(root,1,"Synthetic Monthly HTTP Category")).getId();
    }
    @AfterEach void cleanSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_entry_series_members","financial_entry_series","financial_entry_events","financial_entry_series_command_receipts","financial_entry_command_receipts","financial_entry_command_keys","financial_entries","financial_bank_accounts","financial_categories"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void publicCreationMustReturnOrderedReceiptLocationAndExactProjectedSeriesMetadata()throws Exception {
        var response=command(UUID.randomUUID(),body()).andExpect(status().isCreated()).andExpect(jsonPath("$.items.length()").value(3)).andReturn().getResponse();
        var receipt=json.readTree(response.getContentAsString());var first=entry(receipt,0);assertThat(response.getHeader("Location")).isEqualTo(entryRoute(first));
        mvc.perform(get(entryRoute(first)).with(user(new UserAccountPrincipal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.values.nominalAmount").value("1.23"))
                .andExpect(jsonPath("$.series.id").value(receipt.get("seriesId").asString())).andExpect(jsonPath("$.series.mode").value("MONTHLY"))
                .andExpect(jsonPath("$.series.number").value(1)).andExpect(jsonPath("$.series.quantity").value(3));
        mvc.perform(get(entryRoute(entry(receipt,2))).with(user(new UserAccountPrincipal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.series.number").value(3));
        mvc.perform(get(entriesRoute()).with(user(new UserAccountPrincipal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3));
    }
    @Test void originalPositionAndQuantityMustSurviveDeletionOfAMiddleOccurrence()throws Exception {
        var receipt=json.readTree(command(UUID.randomUUID(),body()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var third=entry(receipt,2);settlement.settle(manager.getId(),owner.getId(),UUID.randomUUID(),third,0L,new EntrySettlementData(LocalDate.of(2026,9,1),new BigDecimal("1.23"),bank));
        mvc.perform(delete(entryRoute(entry(receipt,1))).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).queryParam("expectedVersion","0")).andExpect(status().isOk());
        mvc.perform(get(entryRoute(third)).with(user(new UserAccountPrincipal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.series.number").value(3)).andExpect(jsonPath("$.series.quantity").value(3));
        mvc.perform(get(entriesRoute()).with(user(new UserAccountPrincipal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2));
    }
    @Test void creationReplayAfterRelatedDeletionMustReturnTheOriginalReceiptWithoutNewEntries()throws Exception {
        var key=UUID.randomUUID();var original=command(key,body()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var receipt=json.readTree(original);
        mvc.perform(delete(entryRoute(entry(receipt,0))).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).queryParam("expectedVersion","0")).andExpect(status().isOk());
        assertThat(command(key,body()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).isEqualTo(original);
        assertThat(count("financial_entries")).isZero();assertThat(count("financial_entry_events")).isEqualTo(6);
    }
    @Test void normalizedMonthlyRouteMustUseTheExistingPermissionAndPayloadGateAndRejectForeignReferences()throws Exception {
        var large=" ".repeat(65537);
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(viewer))).with(csrf()).contentType("application/json").content(large)).andExpect(status().isForbidden());
        mvc.perform(post("/api/companies/"+other.getId()+"/financial-entry-series/monthly").with(user(new UserAccountPrincipal(manager))).with(csrf()).contentType("application/json").content(large)).andExpect(status().isForbidden());
        command(UUID.randomUUID(),large).andExpect(status().isPayloadTooLarge());
        var encoded=java.net.URI.create(route().replace("financial-entry-series","%66inancial-entry-series"));
        mvc.perform(post(encoded).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(large)).andExpect(status().isPayloadTooLarge());
        command(UUID.randomUUID(),body().replace(bank.toString(),foreignBank.toString())).andExpect(status().isBadRequest());assertThat(count("financial_entry_series")).isZero();
    }
    @Test void singleEntryPayloadMustRemainCompatibleAndItsKeyCannotCreateAMonthlySet()throws Exception {
        var key=UUID.randomUUID();var data=new EntryCommandJsonDecoder().decode(singleBody().getBytes(StandardCharsets.UTF_8)).values();
        var receipt=individual.create(manager.getId(),owner.getId(),key,data);
        mvc.perform(get(entryRoute(receipt.entryId())).with(user(new UserAccountPrincipal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.series").doesNotExist());
        command(key,body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_MISMATCH"));assertThat(count("financial_entries")).isEqualTo(1);
    }
    ResultActions command(UUID key,String body)throws Exception {return mvc.perform(post(route()).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body));}
    String route() {return "/api/companies/"+owner.getId()+"/financial-entry-series/monthly";}
    String entriesRoute() {return "/api/companies/"+owner.getId()+"/financial-entries";}
    String entryRoute(UUID id) {return entriesRoute()+"/"+id;}
    UUID entry(JsonNode receipt,int index) {return UUID.fromString(receipt.get("items").get(index).get("entryId").asString());}
    String singleBody() {return EntryCommandJsonDecoderTest.valid().replace("00000000-0000-0000-0000-000000000001",bank.toString()).replace("00000000-0000-0000-0000-000000000002",category.toString());}
    String body() {return singleBody().replace("{\"values\":","{\"quantity\":3,\"values\":");}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    UUID bank(Company company) {return banks.saveAndFlush(new BankAccount(company,"Synthetic Monthly HTTP Bank",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true)).getId();}
    Company company(String label,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Monthly HTTP Firm "+label,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Monthly HTTP Client "+label,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Monthly HTTP Company "+label,cnpj));}
    UserAccount createUser(String label) {var actor=users.saveAndFlush(new UserAccount("Synthetic Monthly HTTP "+label,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(actor.getId());return actor;}
}
