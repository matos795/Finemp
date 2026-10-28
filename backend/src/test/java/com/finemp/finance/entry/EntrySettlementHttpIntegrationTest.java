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
import java.net.URI;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"finemp.finance.entries.enabled=true","finemp.finance.entries.commands.enabled=true","finemp.finance.catalog.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EntrySettlementHttpIntegrationTest {
    @Autowired FinancialEntryCommandService creator;@Autowired FinancialEntryRepository entries;
    @Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;@Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
    Company owner,other;UserAccount manager,otherManager,viewer,admin,restricted;UUID bankA,bankB,foreignBank,entry,foreignEntry,createKey;
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","81818181000181");other=company("Other","82828282000182");
        manager=createUser("Manager");otherManager=createUser("Other manager");viewer=createUser("Viewer");admin=createUser("Admin");restricted=createUser("Restricted");
        grant(manager,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));
        grant(otherManager,other,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));
        grant(viewer,owner,CompanyAccessRole.VIEWER,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW));
        grant(admin,owner,CompanyAccessRole.ADMIN,CostCenterScope.ALL,Set.of());grant(restricted,owner,CompanyAccessRole.CUSTOM,CostCenterScope.RESTRICTED,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));
        bankA=bank(owner,"Original");bankB=bank(owner,"Payment");foreignBank=bank(other,"Foreign");
        createKey=UUID.randomUUID();entry=creator.create(manager.getId(),owner.getId(),createKey,entryData(bankA,category(owner))).entryId();
        foreignEntry=creator.create(otherManager.getId(),other.getId(),UUID.randomUUID(),entryData(foreignBank,category(other))).entryId();
    }
    @AfterEach void cleanSyntheticFixturesOnly() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_entry_events","financial_entry_command_receipts","financial_entry_command_keys","financial_entries","financial_bank_accounts","financial_categories"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void paymentAndReversalMustExposeReceiptsAndPersistTheBackendCalculatedState()throws Exception {
        command("settlements",UUID.randomUUID(),payment(0)).andExpect(status().isOk()).andExpect(jsonPath("$.action").value("SETTLED"))
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.entryId").value(entry.toString()));
        mvc.perform(get(base()).with(user(principal(manager)))).andExpect(status().isOk()).andExpect(jsonPath("$.values.nominalAmount").value("100.00"))
                .andExpect(jsonPath("$.values.actualPaidAmount").value("110.50")).andExpect(jsonPath("$.effectiveAmount").value("110.50"));
        command("settlement-reversals",UUID.randomUUID(),reversal(1)).andExpect(status().isOk()).andExpect(jsonPath("$.action").value("SETTLEMENT_REVERSED"))
                .andExpect(jsonPath("$.version").value(2));
        assertThat(state().paymentDate()).isNull();assertThat(state().actualPaidAmount()).isNull();assertThat(state().bankAccountId()).isEqualTo(bankB);
        assertThat(count("financial_entry_events")).isEqualTo(3);assertThat(count("financial_entry_command_receipts")).isEqualTo(3);
    }
    @Test void replayAfterReversalMustReturnTheOriginalPaymentReceiptAndRevocationMustDenyIt()throws Exception {
        var key=UUID.randomUUID();var receipt=command("settlements",key,payment(0)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        command("settlement-reversals",UUID.randomUUID(),reversal(1)).andExpect(status().isOk());
        command("settlements",key,payment(0)).andExpect(status().isOk()).andExpect(content().json(receipt));assertThat(state().paymentDate()).isNull();
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),manager.getId());
        command("settlements",key,payment(0)).andExpect(status().isForbidden());assertThat(count("financial_entry_events")).isEqualTo(3);
    }
    @Test void csrfIdentityExplicitManageScopeAndCompanyMustBeCheckedBeforeBodyConsumption()throws Exception {
        var large=" ".repeat(65537);
        for(var operation:List.of("settlements","settlement-reversals")) {
            mvc.perform(post(base()+"/"+operation).with(user(principal(manager))).contentType("application/json").content(large)).andExpect(status().isForbidden());
            mvc.perform(post(base()+"/"+operation).with(csrf()).contentType("application/json").content(large)).andExpect(status().isUnauthorized());
            for(var actor:List.of(viewer,admin,restricted))mvc.perform(post(base()+"/"+operation).with(user(principal(actor))).with(csrf()).contentType("application/json").content(large)).andExpect(status().isForbidden());
            mvc.perform(post("/api/companies/"+other.getId()+"/financial-entries/"+foreignEntry+"/"+operation).with(user(principal(manager))).with(csrf()).contentType("application/json").content(large)).andExpect(status().isForbidden());
        }
        unchanged();
    }
    @Test void foreignEntriesAndBanksMustNotBeAccessibleThroughTheOwnedRoute()throws Exception {
        mvc.perform(post("/api/companies/"+owner.getId()+"/financial-entries/"+foreignEntry+"/settlements").with(user(principal(manager))).with(csrf())
                .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(payment(0))).andExpect(status().isNotFound());
        command("settlements",UUID.randomUUID(),payment(0).replace(bankB.toString(),foreignBank.toString())).andExpect(status().isBadRequest());
        assertThat(entries.findById(foreignEntry).orElseThrow().getValues().paymentDate()).isNull();unchanged();
    }
    @Test void protocolAndInjectedFieldsMustFailWithFixedMessagesAndNoFinancialArtifacts()throws Exception {
        mvc.perform(post(base()+"/settlements").with(user(principal(manager))).with(csrf()).contentType("application/json").content(payment(0))).andExpect(status().isBadRequest());
        for(var body:List.of(payment(0).replace("\"110.50\"","110.50"),payment(0).replace("\"paymentDate\":","\"actualPaidAmount\":\"1.00\",\"paymentDate\":"),
                payment(0).replace("\"paymentDate\":","\"companyId\":\"injected\",\"paymentDate\":"),payment(0).replace("\"expectedVersion\":0,",""))) {
            var response=command("settlements",UUID.randomUUID(),body).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("110.50",bankB.toString(),"INSERT","SELECT");
        }
        command("settlement-reversals",UUID.randomUUID(),"{\"expectedVersion\":0,\"values\":{}}").andExpect(status().isBadRequest());unchanged();
    }
    @Test void staleVersionAndKeysReusedAcrossActionsMustReturnConflictsWithoutDuplicateStateChanges()throws Exception {
        command("settlements",UUID.randomUUID(),payment(9)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        command("settlements",createKey,payment(0)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_MISMATCH"));
        var key=UUID.randomUUID();command("settlements",key,payment(0)).andExpect(status().isOk());
        command("settlement-reversals",key,reversal(1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_MISMATCH"));
        command("settlements",key,payment(0).replace("110.50","110.51")).andExpect(status().isConflict());assertThat(count("financial_entry_events")).isEqualTo(2);
    }
    @Test void encodedRoutesMustRetainTheSameBodyEncodingAndConcurrencyGates()throws Exception {
        for(var operation:List.of("settlements","settlement-reversals")) {
            var encoded=URI.create(base()+"/%73"+operation.substring(1));
            mvc.perform(post(encoded).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(" ".repeat(65537)))
                    .andExpect(status().isPayloadTooLarge());
            mvc.perform(post(base()+"/"+operation).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).header("Content-Encoding","gzip").contentType("application/json").content(payment(0)))
                    .andExpect(status().isUnsupportedMediaType());
        }
        mvc.perform(post(URI.create(base()+"/%73ettlements")).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(payment(0)))
                .andExpect(status().isOk());
        mvc.perform(post(URI.create(base()+"/%73ettlement-reversals")).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(reversal(1)))
                .andExpect(status().isOk());assertThat(state().paymentDate()).isNull();assertThat(count("financial_entry_events")).isEqualTo(3);
    }
    ResultActions command(String operation,UUID key,String body)throws Exception {return mvc.perform(post(base()+"/"+operation).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body));}
    String base() {return "/api/companies/"+owner.getId()+"/financial-entries/"+entry;}
    String payment(long version) {return EntrySettlementJsonDecoderTest.valid().replace("\"expectedVersion\":0","\"expectedVersion\":"+version).replace("00000000-0000-0000-0000-000000000001",bankB.toString());}
    String reversal(long version) {return "{\"expectedVersion\":"+version+"}";}
    FinancialEntryValues state() {return entries.findById(entry).orElseThrow().getValues();}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    void unchanged() {assertThat(state().paymentDate()).isNull();assertThat(state().bankAccountId()).isEqualTo(bankA);assertThat(count("financial_entry_events")).isEqualTo(1);assertThat(count("financial_entry_command_receipts")).isEqualTo(1);}
    FinancialEntryCommandData entryData(UUID bank,UUID category) {var date=LocalDate.of(2026,7,1);return new FinancialEntryCommandData(FinancialDirection.EXPENSE,"Synthetic HTTP entry",new BigDecimal("100.00"),date,date,YearMonth.of(2026,7),date,null,bank,category,null,null,null,"","SYNTHETIC","");}
    UUID bank(Company company,String label) {return banks.saveAndFlush(new BankAccount(company,"Synthetic "+label,BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true)).getId();}
    UUID category(Company company) {var group=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.EXPENSE,1,"Synthetic Group"));return categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Category")).getId();}
    Company company(String label,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Payment HTTP Firm "+label,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Payment HTTP Client "+label,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Payment HTTP Company "+label,cnpj));}
    UserAccount createUser(String label) {var actor=users.saveAndFlush(new UserAccount("Synthetic Payment HTTP "+label,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(actor.getId());return actor;}
    UserAccountPrincipal principal(UserAccount actor) {return new UserAccountPrincipal(actor);}
    void grant(UserAccount actor,Company company,CompanyAccessRole role,CostCenterScope scope,Set<CompanyPermission> permissions) {grants.saveAndFlush(new CompanyAccessGrant(actor,company,role,scope,permissions));}
}
