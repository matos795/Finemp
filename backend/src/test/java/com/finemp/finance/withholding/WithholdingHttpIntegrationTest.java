package com.finemp.finance.withholding;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
import com.finemp.finance.entry.*;
import com.finemp.finance.period.FinancialPeriodCommandService;
import com.finemp.security.UserAccountPrincipal;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"finemp.finance.withholdings.enabled=true","finemp.finance.entries.enabled=true","finemp.finance.entries.commands.enabled=true","finemp.finance.periods.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WithholdingHttpIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired FinancialPeriodCommandService periods;@Autowired EntryDeletionCommandService deletion;
    @Autowired FinancialEntryRepository entries;@Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;@Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;@Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;
    Company owner,other;UserAccount manager,viewer;FinancialEntry entry;final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();final JsonMapper json=JsonMapper.builder().build();
    @BeforeEach void prepare() {
        owner=company("Owner","50505050000150");other=company("Other","60606060000160");manager=createUser("Manager");viewer=createUser("Viewer");
        grants.saveAndFlush(new CompanyAccessGrant(manager,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE,CompanyPermission.FINANCIAL_PERIOD_CLOSE)));grants.saveAndFlush(new CompanyAccessGrant(viewer,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW)));
        var date=LocalDate.of(2026,7,1);var bank=banks.saveAndFlush(new BankAccount(owner,"Synthetic Tax HTTP Bank",BankAccountType.CASH,BigDecimal.ZERO,date,true));var group=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.EXPENSE,1,"Synthetic Tax HTTP Group"));var sub=categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Tax HTTP Sub"));entry=entries.saveAndFlush(new FinancialEntry(owner,new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic tax HTTP entry",BigDecimal.ONE,date,date,YearMonth.of(2026,7),date,null,null,bank.getId(),sub.getId(),null,null,null,"","",""),manager.getId()));
    }
    @AfterEach void cleanup() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_withholding_command_receipts","financial_tax_withholdings","financial_entry_events","financial_entry_command_receipts","financial_entry_command_keys","financial_entries","financial_period_command_receipts","financial_periods","financial_bank_accounts","financial_categories","access_audit_events"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());companies.deleteById(company.getId());
        }
        for(var id:clientIds)clients.deleteById(id);for(var id:firmIds)firms.deleteById(id);for(var id:userIds)users.deleteById(id);
    }
    @Test void createReadUpdateAndFilterMustKeepMoneyTextAndExplicitTreatment()throws Exception {
        var result=create(UUID.randomUUID()).andExpect(status().isCreated()).andReturn();var receipt=json.readTree(result.getResponse().getContentAsString());var id=receipt.get("withholdingId").asString();String location=result.getResponse().getHeader("Location");assertThat(location).isEqualTo(route()+"/"+id);
        mvc.perform(get(location).with(user(principal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.values.amount").value("1.23")).andExpect(jsonPath("$.values.treatment").value("PAYABLE"));
        mvc.perform(put(location).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(WithholdingJsonDecoderTest.body.replace("\"entryVersion\":0","\"entryVersion\":0,\"expectedVersion\":0").replace("PAYABLE","RECOVERABLE"))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get(route()).with(user(principal(viewer))).param("treatment","RECOVERABLE").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].values.treatment").value("RECOVERABLE"));
    }
    @Test void deletionHistoryAndReplayMustSurviveRemovalOfBothChildAndParent()throws Exception {
        var key=UUID.randomUUID();String original=create(key).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();var id=json.readTree(original).get("withholdingId").asString();
        mvc.perform(post(route()+"/"+id+"/deletions").with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"entryVersion\":0,\"expectedVersion\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DELETED"));
        deletion.delete(manager.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L);
        mvc.perform(get(route()+"/"+id+"/history").with(user(principal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get(route().replace(entry.getId().toString(),UUID.randomUUID().toString())+"/"+id+"/history").with(user(principal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        create(key).andExpect(status().isCreated()).andExpect(content().json(original));assertThat(count("financial_tax_withholdings")).isZero();
    }
    @Test void sharedFinancialGateMustAuthorizeBeforeBodyConsumptionAndRemainTenantScoped()throws Exception {
        var large="x".repeat(65537);
        mvc.perform(post(route()).with(user(principal(viewer))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(large)).andExpect(status().isForbidden());
        var encoded=java.net.URI.create(route().replace("withholdings","%77ithholdings"));mvc.perform(post(encoded).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(large)).andExpect(status().isPayloadTooLarge());
        mvc.perform(get(route().replace(owner.getId().toString(),other.getId().toString())).with(user(principal(manager)))).andExpect(status().isForbidden());
        mvc.perform(post(route()).with(user(principal(manager))).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(WithholdingJsonDecoderTest.body)).andExpect(status().isForbidden());assertThat(count("financial_tax_withholdings")).isZero();
    }
    @Test void closedCompetenceMustReturnConflictButNotPreventOriginalReceiptReplay()throws Exception {
        var key=UUID.randomUUID();create(key).andExpect(status().isCreated());periods.close(manager.getId(),owner.getId(),UUID.randomUUID(),YearMonth.of(2026,7),null);
        create(UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FINANCIAL_PERIOD_CLOSED"));create(key).andExpect(status().isCreated());assertThat(count("financial_tax_withholdings")).isEqualTo(1);
    }
    @Test void strictContractAndParentVersionMustBeEnforcedOnTheServer()throws Exception {
        for(var invalid:List.of(WithholdingJsonDecoderTest.body.replace("\"1.23\"","1.23"),WithholdingJsonDecoderTest.body.replace("1.23","1.005"),WithholdingJsonDecoderTest.body.replace("\"entryVersion\":0","\"entryVersion\":0,\"entryVersion\":1")))mvc.perform(post(route()).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post(route()).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(WithholdingJsonDecoderTest.body.replace("\"entryVersion\":0","\"entryVersion\":5"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_ENTRY_VERSION"));assertThat(count("financial_tax_withholdings")).isZero();
    }
    org.springframework.test.web.servlet.ResultActions create(UUID key)throws Exception{return mvc.perform(post(route()).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(WithholdingJsonDecoderTest.body));}
    String route(){return "/api/companies/"+owner.getId()+"/financial-entries/"+entry.getId()+"/withholdings";}
    UserAccountPrincipal principal(UserAccount actor){return new UserAccountPrincipal(actor);}
    long count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    Company company(String name,String cnpj){var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Tax HTTP Firm "+name,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Tax HTTP Client "+name,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Tax HTTP Company "+name,cnpj));}
    UserAccount createUser(String name){var row=users.saveAndFlush(new UserAccount("Synthetic Tax HTTP "+name,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(row.getId());return row;}
}
