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
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"finemp.finance.entries.enabled=true","finemp.finance.entries.commands.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EntryDeletionHttpIntegrationTest {
    @Autowired FinancialEntryCommandService creator;@Autowired FinancialEntryRepository entries;@Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;
    @Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;@Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;
    @Autowired org.springframework.context.ApplicationContext context;
    Company owner,other;UserAccount manager,viewer,restricted;UUID bank,category,entry,foreignEntry,createKey;
    final LocalDate date=LocalDate.of(2026,7,1);
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","89898989000189");other=company("Other","90909090000190");manager=createUser("Manager");viewer=createUser("Viewer");restricted=createUser("Restricted");
        grant(manager,CostCenterScope.ALL,CompanyPermission.FINANCIAL_ENTRY_MANAGE);grant(viewer,CostCenterScope.ALL,CompanyPermission.FINANCIAL_ENTRY_VIEW);grant(restricted,CostCenterScope.RESTRICTED,CompanyPermission.FINANCIAL_ENTRY_MANAGE);
        bank=bank(owner);category=category(owner);createKey=UUID.randomUUID();entry=creator.create(manager.getId(),owner.getId(),createKey,data(date)).entryId();
        foreignEntry=entries.saveAndFlush(new FinancialEntry(other,new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic foreign HTTP deletion",BigDecimal.ONE,date,date,YearMonth.of(2026,7),date,null,null,bank(other),category(other),null,null,null,"","",""),manager.getId())).getId();
    }
    @AfterEach void cleanSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_entry_events","financial_entry_command_receipts","financial_entry_command_keys","financial_entries","financial_bank_accounts","financial_categories"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void paidAndOpenIndividualsCanBeRemovedAndPublicReceiptDoesNotContainPrivateStates()throws Exception {
        remove(UUID.randomUUID(),entry,0).andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DELETED")).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.before_state").doesNotExist()).andExpect(jsonPath("$.actorId").doesNotExist());
        assertThat(entries.findById(entry)).isEmpty();mvc.perform(get(route()+"/"+entry).with(user(new UserAccountPrincipal(manager)))).andExpect(status().isNotFound());
        var open=creator.create(manager.getId(),owner.getId(),UUID.randomUUID(),data(null));remove(UUID.randomUUID(),open.entryId(),0).andExpect(status().isOk());
        assertThat(count("financial_entry_events")).isEqualTo(4);assertThat(count("financial_entry_command_receipts")).isEqualTo(4);
    }
    @Test void replayAfterRemovalMustReturnIdenticalReceiptAndRevocationMustDenyIt()throws Exception {
        var key=UUID.randomUUID();var original=remove(key,entry,0).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(remove(key,entry,0).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEqualTo(original);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),manager.getId());
        remove(key,entry,0).andExpect(status().isForbidden());assertThat(count("financial_entry_events")).isEqualTo(2);
    }
    @Test void authenticationCsrfAndExplicitManageAreRequiredBeforeDeletion()throws Exception {
        mvc.perform(delete(route()+"/"+entry).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).queryParam("expectedVersion","0")).andExpect(status().isUnauthorized());
        mvc.perform(delete(route()+"/"+entry).with(user(new UserAccountPrincipal(manager))).header("Idempotency-Key",UUID.randomUUID()).queryParam("expectedVersion","0")).andExpect(status().isForbidden());
        for(var actor:List.of(viewer,restricted))mvc.perform(delete(route()+"/"+entry).with(user(new UserAccountPrincipal(actor))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).queryParam("expectedVersion","0")).andExpect(status().isForbidden());
        assertThat(entries.findById(entry)).isPresent();assertThat(count("financial_entry_events")).isEqualTo(1);
    }
    @Test void foreignTargetsAndCompaniesMustRemainInvisibleAndUnchanged()throws Exception {
        remove(UUID.randomUUID(),foreignEntry,0).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FINANCIAL_ENTRY_NOT_FOUND"));
        mvc.perform(delete("/api/companies/"+other.getId()+"/financial-entries/"+foreignEntry).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).queryParam("expectedVersion","0")).andExpect(status().isForbidden());
        assertThat(entries.findById(foreignEntry)).isPresent();assertThat(count("financial_entry_events")).isEqualTo(1);
    }
    @Test void expectedVersionAndSharedActionKeyMustConflictWithoutPartialEffects()throws Exception {
        remove(createKey,entry,0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_MISMATCH"));
        jdbc.update("UPDATE financial_entries SET version=1 WHERE id=?",entry);
        remove(UUID.randomUUID(),entry,0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        var key=UUID.randomUUID();remove(key,entry,1).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        remove(key,entry,0).andExpect(status().isConflict());assertThat(count("financial_entry_events")).isEqualTo(2);
    }
    @Test void invalidVersionOrHeaderAndEncodedRouteMustKeepFixedErrorsWithoutReadingBodies()throws Exception {
        mvc.perform(delete(route()+"/"+entry).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FINANCIAL_ENTRY_COMMAND"));
        remove(UUID.randomUUID(),entry,-1).andExpect(status().isBadRequest());
        mvc.perform(delete(route()+"/"+entry).with(user(new UserAccountPrincipal(manager))).with(csrf()).queryParam("expectedVersion","0")).andExpect(status().isBadRequest());
        var encoded=java.net.URI.create((route()+"/"+entry).replace("financial-entries","%66inancial-entries"));
        mvc.perform(delete(encoded).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).queryParam("expectedVersion","0").contentType("text/plain").content("Ignored body"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DELETED"));assertThat(entries.findById(entry)).isEmpty();
    }
    @Test void formContentFilterMustBeAbsentAndBodyVersionMustNeverDeleteTheEntry()throws Exception {
        assertThat(context.getBeansOfType(org.springframework.web.filter.FormContentFilter.class)).isEmpty();
        mvc.perform(delete(route()+"/"+entry).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID())
                .contentType("application/x-www-form-urlencoded").content("expectedVersion=0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FINANCIAL_ENTRY_COMMAND"));
        assertThat(entries.findById(entry)).isPresent();assertThat(count("financial_entry_events")).isEqualTo(1);assertThat(count("financial_entry_command_receipts")).isEqualTo(1);
        mvc.perform(delete(route()+"/"+entry).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).queryParam("expectedVersion","1")
                .contentType("application/x-www-form-urlencoded").content("expectedVersion=0")).andExpect(status().isConflict());
        assertThat(entries.findById(entry)).isPresent();
    }
    ResultActions remove(UUID key,UUID id,long version)throws Exception {return mvc.perform(delete(route()+"/"+id).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",key).queryParam("expectedVersion",Long.toString(version)));}
    String route() {return "/api/companies/"+owner.getId()+"/financial-entries";}
    FinancialEntryCommandData data(LocalDate paid) {return new FinancialEntryCommandData(FinancialDirection.EXPENSE,"Synthetic HTTP deletion",new BigDecimal("1.23"),date,date,YearMonth.of(2026,7),date,paid,bank,category,null,null,null,"","","");}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    UUID bank(Company company) {return banks.saveAndFlush(new BankAccount(company,"Synthetic HTTP Deletion Bank",BankAccountType.CASH,BigDecimal.ZERO,date,true)).getId();}
    UUID category(Company company) {var group=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.EXPENSE,1,"Synthetic HTTP Deletion Group"));return categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic HTTP Deletion Category")).getId();}
    Company company(String label,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic HTTP Deletion Firm "+label,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic HTTP Deletion Client "+label,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic HTTP Deletion Company "+label,cnpj));}
    UserAccount createUser(String label) {var actor=users.saveAndFlush(new UserAccount("Synthetic HTTP Deletion "+label,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(actor.getId());return actor;}
    void grant(UserAccount actor,CostCenterScope scope,CompanyPermission permission) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,scope,Set.of(permission)));}
}
