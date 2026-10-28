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
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"finemp.finance.entries.enabled=true","finemp.finance.entries.commands.enabled=true","finemp.finance.catalog.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinancialEntryCommandHttpIntegrationTest {
    @Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;
    @Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;
    Company owner,other;UserAccount manager,viewer,admin,restricted;UUID bank,category,foreignBank;
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","77777777000177");other=company("Other","78787878000178");
        manager=createUser("Manager");viewer=createUser("Viewer");admin=createUser("Admin");restricted=createUser("Restricted");
        grant(manager,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));
        grant(viewer,CompanyAccessRole.VIEWER,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW));
        grant(admin,CompanyAccessRole.ADMIN,CostCenterScope.ALL,Set.of());grant(restricted,CompanyAccessRole.CUSTOM,CostCenterScope.RESTRICTED,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));
        bank=bank(owner);foreignBank=bank(other);var group=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.EXPENSE,1,"Synthetic Group"));
        category=categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Category")).getId();
    }
    @AfterEach void cleanSyntheticFixturesOnly() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_entry_events","financial_entry_command_receipts","financial_entries","financial_bank_accounts","financial_categories"))
                jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void createReplayUpdateAndReadMustUseBackendStateAndImmutableReceipts()throws Exception {
        var key=UUID.randomUUID();var first=postCommand(key,body()).andExpect(status().isCreated()).andExpect(jsonPath("$.action").value("CREATED"))
                .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.values").doesNotExist()).andReturn();
        var receipt=first.getResponse().getContentAsString();String id=JsonMapper.builder().build().readTree(receipt).get("entryId").asString();
        postCommand(key,body()).andExpect(status().isCreated()).andExpect(content().json(receipt));
        var change=body().replace("{\"values\":","{\"expectedVersion\":0,\"values\":").replace("1.23","2.34");var updateKey=UUID.randomUUID();
        var updated=mvc.perform(put(route()+"/"+id).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",updateKey).contentType("application/json").content(change))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andReturn().getResponse().getContentAsString();
        mvc.perform(put(route()+"/"+id).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",updateKey).contentType("application/json").content(change))
                .andExpect(status().isOk()).andExpect(content().json(updated));
        mvc.perform(get(route()+"/"+id).with(user(principal(manager)))).andExpect(status().isOk()).andExpect(jsonPath("$.values.nominalAmount").value("2.34"));
        assertThat(count("financial_entries")).isEqualTo(1);assertThat(count("financial_entry_events")).isEqualTo(2);assertThat(count("financial_entry_command_receipts")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT actor_id FROM financial_entry_events WHERE company_id=? AND action='CREATED'",UUID.class,owner.getId())).isEqualTo(manager.getId());
    }
    @Test void missingCsrfAuthenticationAndNonManageGrantsMustNotReachBodyBufferingOrPersistence()throws Exception {
        var large=" ".repeat(65537);
        mvc.perform(post(route()).with(user(principal(manager))).contentType("application/json").content(large)).andExpect(status().isForbidden());
        mvc.perform(post(route()).with(csrf()).contentType("application/json").content(large)).andExpect(status().isUnauthorized());
        for(var actor:List.of(viewer,admin,restricted))mvc.perform(post(route()).with(user(principal(actor))).with(csrf()).contentType("application/json").content(large)).andExpect(status().isForbidden());
        mvc.perform(post("/api/companies/"+other.getId()+"/financial-entries").with(user(principal(manager))).with(csrf()).contentType("application/json").content(large)).andExpect(status().isForbidden());
        empty();
    }
    @Test void permissionRevocationMustPreventReplayAndNewWritesFromAnExistingPrincipal()throws Exception {
        var key=UUID.randomUUID();postCommand(key,body()).andExpect(status().isCreated());var old=principal(manager);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),manager.getId());
        mvc.perform(post(route()).with(user(old)).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body())).andExpect(status().isForbidden());
        assertThat(count("financial_entry_events")).isEqualTo(1);assertThat(count("financial_entry_command_receipts")).isEqualTo(1);
    }
    @Test void divergentReplayStaleVersionAndMissingTargetsMustReturnSafeConflicts()throws Exception {
        var key=UUID.randomUUID();var result=postCommand(key,body()).andExpect(status().isCreated()).andReturn();
        String id=JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("entryId").asString();
        postCommand(key,body().replace("1.23","2.34")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_MISMATCH"));
        var change=body().replace("{\"values\":","{\"expectedVersion\":9,\"values\":");
        mvc.perform(put(route()+"/"+id).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(change))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        mvc.perform(put(route()+"/"+UUID.randomUUID()).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(change))
                .andExpect(status().isNotFound());assertThat(count("financial_entry_events")).isEqualTo(1);
    }
    @Test void foreignReferencesClientInjectedFieldsAndInvalidCanonicalMoneyMustNotProduceArtifacts()throws Exception {
        for(var invalid:List.of(body().replace(bank.toString(),foreignBank.toString()),body().replace("\"1.23\"","1.23"),body().replace("1.23","1.234"),
                body().replace("\"description\":","\"actualPaidAmount\":\"99.00\",\"description\":"),body().replace("{\"values\":","{\"companyId\":\"injected\",\"values\":"),
                body().replace("\"description\":","\"description\":\"duplicate\",\"description\":"))) {
            var response=postCommand(UUID.randomUUID(),invalid).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("Synthetic private description","INSERT","SELECT",foreignBank.toString());
        }
        empty();
    }
    @Test void invalidCommandMetadataAndVersionShapeMustBeRejected()throws Exception {
        mvc.perform(post(route()).with(user(principal(manager))).with(csrf()).contentType("application/json").content(body())).andExpect(status().isBadRequest());
        mvc.perform(post(route()).with(user(principal(manager))).with(csrf()).header("Idempotency-Key","invalid").contentType("application/json").content(body())).andExpect(status().isBadRequest());
        postCommand(UUID.randomUUID(),body().replace("{\"values\":","{\"expectedVersion\":0,\"values\":")).andExpect(status().isBadRequest());
        mvc.perform(put(route()+"/"+UUID.randomUUID()).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body())).andExpect(status().isBadRequest());empty();
    }
    @Test void oversizedCompressedOrUnsupportedBodiesMustFailBeforePersistenceAndDeleteMustRemainDenied()throws Exception {
        postCommand(UUID.randomUUID()," ".repeat(65537)).andExpect(status().isPayloadTooLarge());
        mvc.perform(put(route()+"/1-1-1-1-1").with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID())
                .contentType("application/json").content(" ".repeat(65537))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post(route()).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).header("Content-Encoding","gzip").contentType("application/json").content(body()))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(route()).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("text/plain").content(body())).andExpect(status().isUnsupportedMediaType());
        mvc.perform(delete(route()+"/"+UUID.randomUUID()).with(user(principal(manager))).with(csrf())).andExpect(status().isForbidden());empty();
    }
    ResultActions postCommand(UUID key,String body)throws Exception {return mvc.perform(post(route()).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body));}
    String body() {return EntryCommandJsonDecoderTest.valid().replace("00000000-0000-0000-0000-000000000001",bank.toString()).replace("00000000-0000-0000-0000-000000000002",category.toString());}
    String route() {return "/api/companies/"+owner.getId()+"/financial-entries";}
    void empty() {for(var table:List.of("financial_entries","financial_entry_events","financial_entry_command_receipts"))assertThat(count(table)).isZero();}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    UUID bank(Company company) {return banks.saveAndFlush(new BankAccount(company,"Synthetic HTTP Bank",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true)).getId();}
    Company company(String name,String document) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic HTTP Firm "+name,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic HTTP Client "+name,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic HTTP Company "+name,document));}
    UserAccount createUser(String name) {var row=users.saveAndFlush(new UserAccount("Synthetic HTTP "+name,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(row.getId());return row;}
    void grant(UserAccount actor,CompanyAccessRole role,CostCenterScope scope,Set<CompanyPermission> permissions) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,role,scope,permissions));}
    UserAccountPrincipal principal(UserAccount actor) {return new UserAccountPrincipal(actor);}
}
